// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.ksp)
  alias(libs.plugins.mavenPublish)
}

tasks.compileTestKotlin { compilerOptions { optIn.add("kotlin.ExperimentalStdlibApi") } }

dependencies {
  api(libs.kotlinx.immutable)
  api(libs.moshi)
  kspTest(libs.moshi.codegen)
  testImplementation(libs.moshi.kotlin)
  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
