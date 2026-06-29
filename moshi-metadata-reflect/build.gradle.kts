// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
plugins {
  kotlin("jvm")
  id("com.google.devtools.ksp")
  id("com.vanniktech.maven.publish")
}

tasks.compileTestKotlin {
  compilerOptions {
    optIn.add("kotlin.ExperimentalStdlibApi")
    freeCompilerArgs.add("-Xannotation-default-target=param-property")
  }
}

dependencies {
  api(libs.moshi)
  implementation(libs.kotlin.metadata)

  kspTest(libs.moshi.codegen)
  testImplementation(libs.assertj)
  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
