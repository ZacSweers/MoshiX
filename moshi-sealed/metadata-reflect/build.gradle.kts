// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublish)
}

kotlin {
  compilerOptions {
    freeCompilerArgs.add(
      // https://youtrack.jetbrains.com/issue/KT-73255
      "-Xannotation-default-target=param-property"
    )
  }
}

dependencies {
  api(libs.moshi)
  implementation(project(":moshi-sealed:runtime"))
  implementation(libs.moshi.adapters)
  implementation(libs.kotlin.metadata)
}
