// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublish)
}

tasks.compileTestKotlin { compilerOptions { optIn.add("kotlin.ExperimentalStdlibApi") } }

dependencies {
  api(libs.moshi)
  implementation(libs.okio)
  testImplementation(libs.moshi.kotlin)
  testImplementation(libs.okhttp)
  testImplementation(libs.okhttp.mockwebserver)
  testImplementation(libs.retrofit)
  testImplementation(libs.retrofit.moshi)
  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
