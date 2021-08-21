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
  // TODO use ksp version again when ksp supports 1.4.30
  //  https://github.com/google/ksp/issues/267
//  id("com.google.devtools.ksp") version Dependencies.Kotlin.Ksp.version
  kotlin("jvm")
  kotlin("kapt")
  id("com.vanniktech.maven.publish")
}

dependencies {
  implementation(libs.autoService)
  kapt(libs.autoService.processor)
  kapt(libs.incap.processor)
  compileOnly(libs.incap)

  implementation(libs.autoCommon)
  implementation(libs.kotlinpoet)
  implementation(libs.kotlinpoet.metadataSpecs)
  implementation(libs.moshi.adapters)
  implementation(libs.moshi)
  implementation(project(":moshi-sealed:runtime"))

  testImplementation(libs.kotlinCompileTesting)
  testImplementation(libs.truth)
  testImplementation(libs.junit)
}
