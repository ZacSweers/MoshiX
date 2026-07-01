// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublish)
}

dependencies {
  api(libs.moshi.adapters)
  api(libs.moshi)

  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
