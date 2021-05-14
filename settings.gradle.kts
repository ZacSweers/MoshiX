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

pluginManagement {
  repositories {
    mavenCentral()
    google()
    // Kotlin bootstrap repository, useful for testing against Kotlin dev builds. Usually only tested on CI shadow jobs
    // https://kotlinlang.slack.com/archives/C0KLZSCHF/p1616514468003200?thread_ts=1616509748.001400&cid=C0KLZSCHF
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap") {
      name = "Kotlin-Bootstrap"
      content {
        // this repository *only* contains Kotlin artifacts (don't try others here)
        includeGroupByRegex("org\\.jetbrains.*")
      }
    }
    gradlePluginPortal()
  }
}

rootProject.name = "moshix-root"
include(":moshi-adapters")
include(":moshi-ksp:moshi-ksp")
include(":moshi-ksp:tests")
include(":moshi-metadata-reflect")
include(":moshi-sealed:runtime")
include(":moshi-sealed:codegen")
include(":moshi-sealed:codegen-ksp")
include(":moshi-sealed:reflect")
include(":moshi-sealed:metadata-reflect")
include(":moshi-sealed:sample")

if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_16)) {
  include(":moshi-records-reflect")
  include(":moshi-sealed:java-sealed-reflect")
  include(":moshi-sealed:sealed-interfaces-samples:java")
}
