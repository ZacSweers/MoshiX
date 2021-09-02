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

plugins {
  kotlin("jvm")
  alias(libs.plugins.ksp)
  id("com.vanniktech.maven.publish")
}

dependencies {
  implementation(libs.autoService)
  ksp(libs.autoService.ksp)
  // For access to MessageCollectorBasedKSPLogger
  compileOnly(libs.ksp)
  compileOnly(libs.ksp.api)
  compileOnly(libs.kotlin.compilerEmbeddable)

  implementation(libs.kotlinpoet)
  implementation(libs.moshi.adapters)
  implementation(libs.moshi)
  implementation(project(":moshi-sealed:runtime"))

  testImplementation(libs.ksp.api)
  testImplementation(libs.ksp)
  testImplementation(libs.truth)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinCompileTesting)
  testImplementation(libs.kotlinCompileTesting.ksp)
  testImplementation(libs.kotlin.compilerEmbeddable)
}
