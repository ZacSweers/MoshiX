/*
 * Copyright (C) 2020 Zac Sweers
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
  alias(libs.plugins.ksp)
}

dependencies {
  ksp(project(":moshi-sealed:codegen"))
  ksp(libs.moshi.codegen)
  kspTest(project(":moshi-sealed:codegen"))
  kspTest(libs.moshi.codegen)

  implementation(project(":moshi-sealed:runtime"))
  implementation(libs.moshi.kotlin)
  implementation(project(":moshi-sealed:reflect"))
  implementation(project(":moshi-sealed:metadata-reflect"))

  testImplementation(project(":moshi-adapters"))
  testImplementation(libs.junit)
  testImplementation(libs.truth)
}

val generatedAnnotation = "javax.annotation.processing.Generated"

ksp { arg("moshi.generated", generatedAnnotation) }

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions { freeCompilerArgs.add("-opt-in=kotlin.ExperimentalStdlibApi") }
}
