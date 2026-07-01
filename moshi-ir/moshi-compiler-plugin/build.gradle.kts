// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.dokka)
  `java-test-fixtures`
  alias(libs.plugins.mavenPublish)
  alias(libs.plugins.ksp)
  alias(libs.plugins.lint)
  idea
}

sourceSets {
  test {
    java.setSrcDirs(listOf("test-gen/java"))
    kotlin.setSrcDirs(listOf("src/test/kotlin"))
    resources.setSrcDirs(listOf("testData"))
  }
}

idea { module.generatedSourceDirs.add(projectDir.resolve("test-gen/java")) }

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions {
    freeCompilerArgs.addAll("-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
  }
}

val moshiRuntime = configurations.dependencyScope("moshiRuntime")

val moshiRuntimeClasspath =
  configurations.resolvable("moshiRuntimeClasspath") { extendsFrom(moshiRuntime.get()) }

dependencies {
  implementation(libs.moshi)
  ksp(libs.autoService.ksp)

  compileOnly(libs.kotlin.stdlib)
  compileOnly(libs.kotlin.compilerEmbeddable)
  compileOnly(libs.autoService)
  runtimeOnly(libs.moshi.kotlinCodegen)

  testFixturesApi(libs.kotlin.testJunit5)
  testFixturesApi(libs.kotlin.compilerTestFramework)
  testFixturesApi(libs.kotlin.compiler)

  add("moshiRuntime", libs.moshi)
  add("moshiRuntime", project(":moshi-sealed:runtime"))

  // Dependencies required to run the internal test framework.
  testRuntimeOnly(libs.junit)
  testRuntimeOnly(libs.kotlin.reflect)
  testRuntimeOnly(libs.kotlin.test)
  testRuntimeOnly(libs.kotlin.scriptRuntime)
  testRuntimeOnly(libs.kotlin.annotationsJvm)
}

tasks.test {
  dependsOn(moshiRuntimeClasspath)
  val moshiRuntimeClasspath = moshiRuntimeClasspath.map { it.asPath }

  useJUnitPlatform()
  workingDir = rootDir

  systemProperty("moshix.jvmTarget", libs.versions.jvmTarget.get())

  doFirst { systemProperty("moshiRuntime.classpath", moshiRuntimeClasspath.get()) }

  // Properties required to run the internal test framework.
  setLibraryProperty("org.jetbrains.kotlin.test.kotlin-stdlib", "kotlin-stdlib")
  setLibraryProperty("org.jetbrains.kotlin.test.kotlin-stdlib-jdk8", "kotlin-stdlib-jdk8")
  setLibraryProperty("org.jetbrains.kotlin.test.kotlin-reflect", "kotlin-reflect")
  setLibraryProperty("org.jetbrains.kotlin.test.kotlin-test", "kotlin-test")
  setLibraryProperty("org.jetbrains.kotlin.test.kotlin-script-runtime", "kotlin-script-runtime")
  setLibraryProperty("org.jetbrains.kotlin.test.kotlin-annotations-jvm", "kotlin-annotations-jvm")

  systemProperty("idea.ignore.disabled.plugins", "true")
  systemProperty("idea.home.path", rootDir)
}

val generateTests =
  tasks.register<JavaExec>("generateTests") {
    inputs
      .dir(layout.projectDirectory.dir("testData"))
      .withPropertyName("testData")
      .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(layout.projectDirectory.dir("test-gen")).withPropertyName("generatedTests")

    classpath = sourceSets.testFixtures.get().runtimeClasspath
    mainClass.set("dev.zacsweers.moshix.ir.compiler.GenerateTestsKt")
    workingDir = rootDir
  }

tasks.compileTestKotlin { dependsOn(generateTests) }

tasks.compileTestJava { dependsOn(generateTests) }

tasks
  .matching { it.name == "lintAnalyzeJvmTest" || it.name == "generateJvmTestLintModel" }
  .configureEach { dependsOn(generateTests) }

fun Test.setLibraryProperty(propName: String, jarName: String) {
  val path =
    project.configurations.testRuntimeClasspath
      .get()
      .files
      .find { """$jarName-\d.*jar""".toRegex().matches(it.name) }
      ?.absolutePath ?: return
  systemProperty(propName, path)
}
