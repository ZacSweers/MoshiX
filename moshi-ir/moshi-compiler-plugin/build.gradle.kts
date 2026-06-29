// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.dokka)
  alias(libs.plugins.mavenPublish)
  alias(libs.plugins.ksp)
  alias(libs.plugins.lint)
}

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions {
    freeCompilerArgs.addAll("-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
  }
}

tasks.withType<Test>().configureEach {
  systemProperty("moshix.jvmTarget", libs.versions.jvmTarget.get())
}

dependencies {
  implementation(libs.moshi)
  ksp(libs.autoService.ksp)

  compileOnly(libs.kotlin.stdlib)
  compileOnly(libs.kotlin.compilerEmbeddable)
  compileOnly(libs.autoService)
  runtimeOnly(libs.moshi.kotlinCodegen)

  testImplementation(libs.kotlin.reflect)
  testImplementation(libs.kotlin.compilerEmbeddable)
  testImplementation(libs.kotlinCompileTesting)
  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testRuntimeOnly(project(":moshi-sealed:runtime"))
}
