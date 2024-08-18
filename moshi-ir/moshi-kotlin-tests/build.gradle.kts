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

moshi { enableSealed.set(true) }

val proguardRuleValidator =
  tasks.register("validateProguardRules") {
    doNotTrackState("This is a validation task that should always run")
    notCompatibleWithConfigurationCache("This task always runs")
    doLast {
      logger.lifecycle("Validating proguard rules")
      val proguardRulesDir = project.file("build/generated/ksp/test/resources/META-INF/proguard")
      check(proguardRulesDir.exists() && proguardRulesDir.listFiles()!!.isNotEmpty()) {
        "No proguard rules found! Did you forget to apply the KSP Gradle plugin?"
      }
      logger.lifecycle("Proguard rules properly generated ✅ ")
    }
  }

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions { freeCompilerArgs.addAll("-opt-in=kotlin.ExperimentalStdlibApi") }
  if (name == "compileTestKotlin" && providers.gradleProperty("kotlin.experimental.tryK2").orNull != "true") {
    finalizedBy(proguardRuleValidator)
  }
}

dependencies {
  testImplementation("junit:junit:4.13.2")
  testImplementation("com.google.truth:truth:1.4.4")
  testImplementation("com.squareup.moshi:moshi:1.15.1")
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
    substitute(module("dev.zacsweers.moshix:moshi-proguard-rule-gen"))
      .using(project(":moshi-proguard-rule-gen"))
  }
}
