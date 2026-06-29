// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.ksp)
}

dependencies {
  api(libs.moshi)
  api(project(":moshi-sealed:runtime"))
  implementation(libs.moshi.adapters)

  ksp(project(":moshi-sealed:codegen"))
  ksp(libs.moshi.codegen)
  kspTest(project(":moshi-sealed:codegen"))
  kspTest(libs.moshi.codegen)

  testImplementation(libs.moshi.kotlin)
  testImplementation(project(":moshi-sealed:reflect"))
  testImplementation(project(":moshi-sealed:metadata-reflect"))
  testImplementation(project(":moshi-adapters"))
  testImplementation(libs.junit)
  testImplementation(libs.truth)
}

val generatedAnnotation = "javax.annotation.processing.Generated"

ksp { arg("moshi.generated", generatedAnnotation) }

kotlin {
  compilerOptions {
    optIn.add("kotlin.ExperimentalStdlibApi")
    freeCompilerArgs.add(
      // https://youtrack.jetbrains.com/issue/KT-73255
      "-Xannotation-default-target=param-property"
    )
  }
}
