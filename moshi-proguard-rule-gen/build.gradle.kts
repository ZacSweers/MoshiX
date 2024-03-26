/*
 * Copyright (C) 2023 Zac Sweers
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
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.dokka)
  alias(libs.plugins.mavenPublish)
  alias(libs.plugins.ksp)
}

kotlin { compilerOptions.optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi") }

tasks.test {
  systemProperty("moshix.jvmTarget", libs.versions.jvmTarget.get())
  // KSP2 needs more memory to run
  minHeapSize = "1024m"
  maxHeapSize = "1024m"
}

dependencies {
  compileOnly(libs.kotlin.compilerEmbeddable)
  compileOnly(libs.ksp.api)

  implementation(libs.autoService)
  implementation(libs.kotlinpoet)
  implementation(libs.kotlinpoet.ksp)
  implementation(libs.moshi)
  implementation(libs.moshi.kotlinCodegen)

  ksp(libs.autoService.ksp)

  testImplementation(libs.truth)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinCompileTesting)
  testImplementation(libs.kotlinCompileTesting.ksp)
  testImplementation(project(":moshi-sealed:runtime"))
}
