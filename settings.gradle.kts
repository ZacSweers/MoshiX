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

pluginManagement {
  resolutionStrategy {
    eachPlugin {
      when (requested.id.id) {
        "symbol-processing" ->
          useModule("com.google.devtools.ksp:symbol-processing:${requested.version}")
      }
    }
  }
  repositories {
    mavenCentral()
    gradlePluginPortal()
    jcenter()
    google()
  }
}

rootProject.name = "moshix-root"
include("ksp-test-util")
include("moshi-adapters")
include("moshi-ksp:moshi-ksp")
include("moshi-ksp:tests")
include("moshi-metadata-reflect")
include("moshi-sealed:runtime")
include("moshi-sealed:codegen")
include("moshi-sealed:codegen-ksp")
include("moshi-sealed:reflect")
include("moshi-sealed:sample")
