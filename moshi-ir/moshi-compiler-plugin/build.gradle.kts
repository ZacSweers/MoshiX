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
    @Suppress("SuspiciousCollectionReassignment") freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
  }
}

dependencies {
  //  compileOnly(kotlin("compiler"))
  compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.6.21")
  implementation("com.google.auto.service:auto-service-annotations:1.0.1")
  implementation("com.squareup.moshi:moshi:1.13.0")
  implementation("com.squareup:kotlinpoet:1.11.0")
  implementation("com.squareup.moshi:moshi-kotlin-codegen:1.13.0")
  ksp("dev.zacsweers.autoservice:auto-service-ksp:1.0.0")

  testImplementation("org.jetbrains.kotlin:kotlin-reflect:1.6.21")
  //  testImplementation(kotlin("compiler"))
  testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.6.21")
  testImplementation("com.github.tschuchortdev:kotlin-compile-testing:1.4.8")
  testImplementation("junit:junit:4.13.2")
  testImplementation("com.google.truth:truth:1.1.3")
}
