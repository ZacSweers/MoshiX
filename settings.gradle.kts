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
    // Kotlin dev (previously bootstrap) repository, useful for testing against Kotlin dev builds.
    // Usually only tested on CI shadow jobs
    // https://kotlinlang.slack.com/archives/C0KLZSCHF/p1616514468003200?thread_ts=1616509748.001400&cid=C0KLZSCHF
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev/") {
      name = "Kotlin-Dev"
      content {
        // this repository *only* contains Kotlin artifacts (don't try others here)
        includeGroupByRegex("org\\.jetbrains.*")
      }
    }
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  versionCatalogs {
    if (System.getenv("DEP_OVERRIDES") == "true") {
      val overrides = System.getenv().filterKeys { it.startsWith("DEP_OVERRIDE_") }
      for (catalog in this) {
        for ((key, value) in overrides) {
          // Case-sensitive, don't adjust it after removing the prefix!
          val catalogKey = key.removePrefix("DEP_OVERRIDE_")
          println("Overriding $catalogKey with $value")
          catalog.version(catalogKey, value)
        }
      }
    }
  }
  repositories {
    mavenCentral()
    // Kotlin dev (previously bootstrap) repository, useful for testing against Kotlin dev builds.
    // Usually only tested on CI shadow jobs
    // https://kotlinlang.slack.com/archives/C0KLZSCHF/p1616514468003200?thread_ts=1616509748.001400&cid=C0KLZSCHF
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev/") {
      name = "Kotlin-Dev"
      content {
        // this repository *only* contains Kotlin artifacts (don't try others here)
        includeGroupByRegex("org\\.jetbrains.*")
      }
    }
  }
}

rootProject.name = "moshix-root"

include(
  ":moshi-adapters",
  ":moshi-ir:moshi-compiler-plugin",
  ":moshi-ir:moshi-kotlin-tests",
  ":moshi-ir:moshi-kotlin-tests:extra-moshi-test-module",
  ":moshi-metadata-reflect",
  ":moshi-sealed:codegen",
  ":moshi-sealed:java-sealed-reflect",
  ":moshi-sealed:metadata-reflect",
  ":moshi-sealed:reflect",
  ":moshi-sealed:runtime",
  ":moshi-sealed:sample",
  ":moshi-sealed:sealed-interfaces-samples:java",
)

includeBuild("moshi-ir/moshi-gradle-plugin") {
  dependencySubstitution {
    substitute(module("dev.zacsweers.moshix:moshi-gradle-plugin")).using(project(":"))
  }
}

enableFeaturePreview("VERSION_CATALOGS")
