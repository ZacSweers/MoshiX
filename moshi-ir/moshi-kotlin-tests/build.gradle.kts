/*
 * Copyright (C) 2021 Zac Sweers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.nio.file.FileSystems
import kotlin.io.path.deleteIfExists
import org.gradle.language.base.plugins.LifecycleBasePlugin.BUILD_GROUP
import org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation.Companion.TEST_COMPILATION_NAME

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.moshix)
}

kotlin.compilerOptions.optIn.add("kotlin.ExperimentalStdlibApi")

moshi { enableSealed.set(true) }

kotlin {
  compilerOptions {
    freeCompilerArgs.add(
      // https://youtrack.jetbrains.com/issue/KT-73255
      "-Xannotation-default-target=param-property"
    )
  }
}

val r8Test = gradle.startParameter.taskNames.any { it.contains("testR8", ignoreCase = true) }

dependencies {
  testImplementation(libs.moshi.adapters)
  testImplementation("junit:junit:4.13.2")
  testImplementation("com.google.truth:truth:1.4.5")
  testImplementation(project(":moshi-ir:moshi-kotlin-tests:extra-moshi-test-module"))
  testImplementation(project(":moshi-adapters"))

  if (!r8Test) {
    testImplementation(kotlin("reflect"))
    testRuntimeOnly(libs.moshi.kotlin)
  }
}

configurations.configureEach {
  resolutionStrategy.dependencySubstitution {
    substitute(module("dev.zacsweers.moshix:moshi-compiler-plugin"))
      .using(project(":moshi-ir:moshi-compiler-plugin"))
    substitute(module("dev.zacsweers.moshix:moshi-sealed-runtime"))
      .using(project(":moshi-sealed:runtime"))
    substitute(module("dev.zacsweers.moshix:moshix-runtime")).using(project(":moshix-runtime"))
  }
}

// R8 test infrastructure
val r8Configuration: Configuration by configurations.creating

dependencies { r8Configuration("com.android.tools:r8:8.13.19") }

abstract class BaseR8Task : JavaExec() {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val mainJarProp: RegularFileProperty

  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val testJarProp: RegularFileProperty

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val testDependencyFilesProp: ConfigurableFileCollection

  fun r8ArgumentProvider(): CommandLineArgumentProvider {
    return CommandLineArgumentProvider {
      buildList {
        addAll(computeArgs())
        testDependencyFilesProp.files
          .filter { it.isFile() }
          .forEach { file -> add(file.absolutePath) }
        add(mainJarProp.get().asFile.absolutePath)
        add(testJarProp.get().asFile.absolutePath)
      }
    }
  }

  abstract fun computeArgs(): Iterable<String>

  fun configureR8Inputs(
    mainJar: Provider<RegularFile>,
    testJar: Provider<RegularFile>,
    testDependencyFiles: Provider<FileCollection>,
  ) {
    mainJarProp.set(mainJar)
    testJarProp.set(testJar)
    testDependencyFilesProp.from(testDependencyFiles)
  }
}

abstract class ExtractR8Rules : BaseR8Task() {
  @get:OutputFile abstract val r8Rules: RegularFileProperty

  override fun computeArgs(): Iterable<String> {
    return buildList {
      add("--rules-output")
      add(r8Rules.get().asFile.absolutePath)
      add("--include-origin-comments")
    }
  }
}

abstract class R8Task : BaseR8Task() {
  @get:Input abstract val javaHome: Property<String>

  @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val r8Rules: RegularFileProperty

  @get:OutputFile abstract val mapping: RegularFileProperty

  @get:OutputFile abstract val r8Jar: RegularFileProperty

  override fun computeArgs(): Iterable<String> {
    return buildList {
      add("--classfile")
      add("--output")
      add(r8Jar.get().asFile.absolutePath)
      add("--pg-conf")
      add(r8Rules.get().asFile.absolutePath)
      add("--pg-map-output")
      add(mapping.get().asFile.absolutePath)
      add("--lib")
      add(javaHome.get())
    }
  }
}

kotlin.target {
  val target = this

  val testCompilation = target.compilations.named(TEST_COMPILATION_NAME)

  val mainJarTask = tasks.named<Jar>(target.artifactsTaskName)
  val mainJar = mainJarTask.flatMap { it.archiveFile }

  val testJar =
    tasks
      .register<Jar>("${target.name}TestJar") {
        from(testCompilation.map { it.output.allOutputs })
        archiveBaseName = base.archivesName.map { it + '-' + target.name }
        archiveClassifier = "tests"
      }
      .flatMap { it.archiveFile }

  val testDependencyFiles = testCompilation.map { it.runtimeDependencyFiles }

  val r8RulesExtractTask =
    tasks.register<ExtractR8Rules>("extractR8Rules") {
      group = BUILD_GROUP
      description = "Extracts R8 rules from jars on the classpath."

      inputs.files(r8Configuration)

      classpath(r8Configuration)
      mainClass.set("com.android.tools.r8.ExtractR8Rules")

      r8Rules.set(layout.buildDirectory.file("shrinker/r8.txt"))
      configureR8Inputs(mainJar, testJar, testDependencyFiles)
      argumentProviders += r8ArgumentProvider()
    }

  val r8Task =
    tasks.register<R8Task>("testJarR8") {
      group = BUILD_GROUP
      description = "Assembles an archive containing the test classes run through R8."

      inputs.files(r8Configuration)

      classpath(r8Configuration)
      mainClass.set("com.android.tools.r8.R8")

      javaHome.set(providers.systemProperty("java.home"))
      r8Rules.set(r8RulesExtractTask.flatMap { it.r8Rules })
      r8Jar.set(layout.buildDirectory.file("libs/${base.archivesName.get()}-testsR8.jar"))
      mapping.set(layout.buildDirectory.file("libs/${base.archivesName.get()}-mapping.txt"))
      configureR8Inputs(mainJar, testJar, testDependencyFiles)
      argumentProviders += r8ArgumentProvider()

      doLast {
        // Quick work around for https://issuetracker.google.com/issues/134372167.
        FileSystems.newFileSystem(r8Jar.get().asFile.toPath(), null as ClassLoader?).use { fs ->
          val root = fs.rootDirectories.first()
          listOf("module-info.class", "META-INF/versions/9/module-info.class").forEach { path ->
            val file = root.resolve(path)
            file.deleteIfExists()
          }
        }
      }
    }

  tasks.register<Test>("testR8") {
    group = VERIFICATION_GROUP
    description = "Runs the unit tests with R8-processed classes."

    dependsOn(r8Task)
    classpath = project.files(r8Task.map { it.r8Jar })
    testClassesDirs = project.files(testDependencyFiles)

    systemProperty("moshi.r8Test", "true")
  }
}
