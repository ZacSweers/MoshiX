/*
 * Copyright (c) 2019 Zac Sweers
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
plugins {
  id("symbol-processing") version Dependencies.Kotlin.Ksp.version
  kotlin("jvm")
  kotlin("kapt")
  id("com.vanniktech.maven.publish")
}

// Necessary to ensure the generated service file is included in the jar
sourceSets {
  main {
    resources {
      srcDir("build/generated/ksp/src/main/resources")
    }
  }
}

val compileKotlin = tasks.named("compileKotlin")
tasks.named<ProcessResources>("processResources").configure {
  dependsOn(compileKotlin)
}

dependencies {
  ksp(project(":misc:auto-service-ksp"))
  implementation(Dependencies.AutoService.annotations)
  kapt(Dependencies.Incap.processor)
  compileOnly(Dependencies.Incap.annotations)

  implementation(Dependencies.autoCommon)
  implementation(Dependencies.KotlinPoet.kotlinPoet)
  implementation(Dependencies.KotlinPoet.metadata)
  implementation(Dependencies.KotlinPoet.metadataSpecs)
  implementation(Dependencies.Moshi.adapters)
  implementation(Dependencies.Moshi.moshi)
  implementation(project(":moshi-sealed:annotations"))
}
