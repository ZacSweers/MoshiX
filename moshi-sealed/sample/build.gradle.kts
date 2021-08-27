/*
 * Copyright (C) 2020 Zac Sweers
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
  alias(libs.plugins.ksp)
  kotlin("jvm")
  kotlin("kapt")
}

val useKsp = findProperty("moshix.useKsp")?.toString()?.toBoolean() ?: false

dependencies {
  if (useKsp) {
    ksp(project(":moshi-sealed:codegen-ksp"))
    ksp(project(":moshi-ksp:moshi-ksp"))
    kspTest(project(":moshi-sealed:codegen-ksp"))
    kspTest(project(":moshi-ksp:moshi-ksp"))
  } else {
    kapt(project(":moshi-sealed:codegen"))
    kapt(libs.moshi.codegen)
  }

  implementation(project(":moshi-sealed:runtime"))
  implementation(libs.moshi.kotlin)
  implementation(project(":moshi-sealed:reflect"))
  implementation(project(":moshi-sealed:metadata-reflect"))

  if (!useKsp) {
    kaptTest(project(":moshi-sealed:codegen"))
  }
  testImplementation(libs.junit)
  testImplementation(libs.truth)
}

// TODO https://github.com/google/ksp/issues/593
// ksp {
//  arg("moshi.generated", generatedAnnotation)
// }

kapt {
  arguments {
    arg("moshi.generated", "javax.annotation.processing.Generated")
  }
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    @Suppress("SuspiciousCollectionReassignment")
    freeCompilerArgs += "-Xopt-in=kotlin.ExperimentalStdlibApi"
  }
}
