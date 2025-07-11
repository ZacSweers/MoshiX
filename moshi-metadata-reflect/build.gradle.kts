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
  implementation(libs.kotlin.metadata)
  implementation(libs.moshi)
  kspTest(libs.moshi.codegen)
  testImplementation(libs.assertj)
  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
