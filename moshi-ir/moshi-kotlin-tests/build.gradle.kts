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
}

moshi { enableSealed.set(true) }

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions { freeCompilerArgs.addAll("-opt-in=kotlin.ExperimentalStdlibApi") }
}

dependencies {
  testImplementation("junit:junit:4.13.2")
  testImplementation("com.google.truth:truth:1.1.3")
  testImplementation("com.squareup.moshi:moshi:1.14.0")
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
  }
}
