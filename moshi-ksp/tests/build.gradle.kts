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
  kotlin("jvm")
  alias(libs.plugins.ksp)
}

sourceSets {
  test {
    java {
      srcDir("build/generated/ksp/test/kotlin")
    }
  }
}

dependencies {
  kspTest(project(":moshi-ksp:moshi-ksp"))
  testImplementation(project(":moshi-ksp:extra-moshi-test-module"))
  testImplementation(libs.moshi)
  testImplementation(project(":moshi-metadata-reflect"))
  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.kotlin.reflect)
}

ksp {
  arg("moshi.generated", "javax.annotation.processing.Generated")
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    @Suppress("SuspiciousCollectionReassignment")
    freeCompilerArgs += listOf("-Xopt-in=kotlin.ExperimentalStdlibApi", "-Xinline-classes")
  }
}
