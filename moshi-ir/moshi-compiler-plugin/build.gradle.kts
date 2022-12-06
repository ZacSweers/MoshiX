/*
 * Copyright (C) 2021 Zac Sweers
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

import com.github.jengelman.gradle.plugins.shadow.tasks.ConfigureShadowRelocation
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.dokka)
  alias(libs.plugins.mavenPublish)
  alias(libs.plugins.ksp)
  alias(libs.plugins.mavenShadow)
}

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions {
    freeCompilerArgs.addAll(
      "-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi",
      "-opt-in=com.squareup.anvil.annotations.ExperimentalAnvilApi",
    )
  }
}

tasks.withType<Test>().configureEach {
  systemProperty("moshix.jvmTarget", libs.versions.jvmTarget.get())
}

val shade: Configuration = configurations.maybeCreate("compileShaded")

configurations.getByName("compileOnly").extendsFrom(shade)

dependencies {
  //  compileOnly(kotlin("compiler"))
  compileOnly(libs.kotlin.compilerEmbeddable)
  implementation(libs.autoService)
  implementation(libs.moshi)
  implementation(libs.kotlinpoet)
  implementation(libs.moshi.kotlinCodegen)
  shade(libs.anvilUtils) {
    exclude(group = "org.jetbrains.kotlin")
    exclude(group = "com.squareup", module = "kotlinpoet")
  }
  ksp(libs.autoService.ksp)

  testImplementation(libs.anvilUtils) // Included in tests due to shading
  testImplementation(libs.kotlin.reflect)
  //  testImplementation(kotlin("compiler"))
  testImplementation(libs.kotlin.compilerEmbeddable)
  testImplementation(libs.kotlinCompileTesting)
  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(project(":moshi-sealed:runtime"))
}

val relocateShadowJar =
  tasks.register<ConfigureShadowRelocation>("relocateShadowJar") { target = tasks.shadowJar.get() }

val shadowJar =
  tasks.shadowJar.apply {
    configure {
      dependsOn(relocateShadowJar)
      archiveClassifier.set("")
      configurations = listOf(shade)
      val shadedPrefix = "dev.zacsweers.moshix.ir.compiler.anvil"
      relocate("com.squareup.anvil.compiler.internal", "$shadedPrefix.compiler.internal")
      relocate(
        "com.squareup.anvil.compiler.internal.reference",
        "$shadedPrefix.compiler.internal.reference"
      )
      relocate("com.squareup.anvil.compiler.api", "$shadedPrefix.compiler.api")
      relocate("com.squareup.anvil.annotations", "$shadedPrefix.annotations")
      relocate("com.squareup.anvil.annotations.compat", "$shadedPrefix.annotations.compat")
    }
  }

artifacts {
  runtimeOnly(shadowJar)
  archives(shadowJar)
}
