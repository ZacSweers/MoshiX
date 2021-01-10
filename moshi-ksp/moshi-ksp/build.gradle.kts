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
  id("com.google.devtools.ksp") version Dependencies.Kotlin.Ksp.version
  kotlin("jvm")
  id("com.vanniktech.maven.publish")
  id("dev.zacsweers.autoservice.ksp")
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    @Suppress("SuspiciousCollectionReassignment")
    freeCompilerArgs += listOf("-Xopt-in=kotlin.RequiresOptIn")
  }
}

dependencies {
  implementation(Dependencies.AutoService.annotations)
  compileOnly(Dependencies.Kotlin.Ksp.api)

  implementation(Dependencies.asm)
  implementation(Dependencies.KotlinPoet.kotlinPoet)
  implementation(Dependencies.Moshi.moshi)

  testImplementation(Dependencies.Kotlin.Ksp.api)
  testImplementation(Dependencies.Testing.truth)
  testImplementation(Dependencies.Testing.junit)
  testImplementation(Dependencies.Kotlin.Ksp.ksp)
  testImplementation(Dependencies.Kotlin.reflect)
  // TODO re-enable with new release
//  testImplementation(Dependencies.Testing.kspCompileTesting)
  testImplementation(Dependencies.Kotlin.Ksp.ksp)
  testImplementation(Dependencies.Testing.compileTesting)
  testImplementation(Dependencies.Kotlin.compilerEmbeddable)
}
