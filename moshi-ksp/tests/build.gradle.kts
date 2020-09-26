/*
 * Copyright (c) 2020 Zac Sweers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("symbol-processing") version Dependencies.Kotlin.Ksp.version
  kotlin("jvm")
}

val generatedAnnotation = if (JavaVersion.current().isJava10Compatible) {
  "javax.annotation.processing.Generated"
} else {
  "javax.annotation.Generated"
}

dependencies {
  ksp(project(":moshi-ksp:moshi-ksp"))
  testImplementation(project(":moshi-ktx"))
  testImplementation(Dependencies.Testing.junit)
  testImplementation(Dependencies.Testing.truth)
}

ksp {
  arg("moshi.generated", generatedAnnotation)
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    @Suppress("SuspiciousCollectionReassignment")
    freeCompilerArgs += "-Xopt-in=kotlin.ExperimentalStdlibApi"
  }
}
