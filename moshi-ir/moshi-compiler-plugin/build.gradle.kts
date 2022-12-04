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
  kotlin("jvm")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish")
  id("com.google.devtools.ksp")
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    @Suppress("SuspiciousCollectionReassignment")
    this.freeCompilerArgs += "-opt-in=com.squareup.anvil.annotations.ExperimentalAnvilApi"
  }
}

tasks.withType<Test>().configureEach {
  systemProperty("moshix.jvmTarget", libs.versions.jvmTarget.get())
}

dependencies {
  //  compileOnly(kotlin("compiler"))
  compileOnly(libs.kotlin.compilerEmbeddable)
  implementation(libs.autoService)
  implementation(libs.moshi)
  implementation(libs.kotlinpoet)
  implementation(libs.moshi.kotlinCodegen)
  // TODO shade this
  implementation(libs.anvilUtils)
  ksp(libs.autoService.ksp)

  testImplementation(libs.kotlin.reflect)
  //  testImplementation(kotlin("compiler"))
  testImplementation(libs.kotlin.compilerEmbeddable)
  testImplementation(libs.kotlinCompileTesting)
  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(project(":moshi-sealed:runtime"))
}
