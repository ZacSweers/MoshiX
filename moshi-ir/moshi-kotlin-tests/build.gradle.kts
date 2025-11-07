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

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.moshix)
  alias(libs.plugins.ksp)
}

kotlin.compilerOptions.optIn.add("kotlin.ExperimentalStdlibApi")

moshi { enableSealed.set(true) }

@CacheableTask
abstract class ProguardRuleValidator : DefaultTask() {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val proguardRulesDir: DirectoryProperty

  @get:OutputFile abstract val output: RegularFileProperty

  @TaskAction
  fun validate() {
    logger.lifecycle("Validating proguard rules")
    val proguardRulesDir = this@ProguardRuleValidator.proguardRulesDir.asFile.get()
    check(proguardRulesDir.exists() && proguardRulesDir.walkTopDown().any()) {
      "No proguard rules found! Did you forget to apply the KSP Gradle plugin?"
    }
    logger.lifecycle("Proguard rules properly generated ✅ ")
    output.get().asFile.writeText("validated")
  }
}

val proguardRuleValidator =
  tasks.register<ProguardRuleValidator>("validateProguardRules") {
    proguardRulesDir.set(
      project.layout.buildDirectory.dir("generated/ksp/test/resources/META-INF/proguard")
    )
    output.set(project.layout.buildDirectory.file("moshix/validation/validated.txt"))
    dependsOn(tasks.withType<KotlinCompile>().named { it == "compileTestKotlin" })
  }

kotlin {
  compilerOptions {
    freeCompilerArgs.add(
      // https://youtrack.jetbrains.com/issue/KT-73255
      "-Xannotation-default-target=param-property"
    )
  }
}

dependencies {
  testImplementation("junit:junit:4.13.2")
  testImplementation("com.google.truth:truth:1.4.5")
  testImplementation(libs.moshi)
  testImplementation(kotlin("reflect"))
  testImplementation(project(":moshi-ir:moshi-kotlin-tests:extra-moshi-test-module"))
  testImplementation(project(":moshi-adapters"))
  testImplementation(libs.moshi.kotlin)
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
