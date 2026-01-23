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
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.ksp)
  alias(libs.plugins.mavenPublish)
}

kotlin { compilerOptions.optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi") }

tasks.test {
  // KSP2 needs more memory to run
  minHeapSize = "2096m"
  maxHeapSize = "2096m"
  // --add-opens for kapt to work. KGP covers this for us but local JVMs in tests do not
  jvmArgs(
    "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
  )
}

dependencies {
  compileOnly(libs.autoService)
  ksp(libs.autoService.ksp)
  compileOnly(libs.ksp.api)
  compileOnly(libs.kotlin.compilerEmbeddable)

  implementation(libs.kotlinpoet)
  implementation(libs.kotlinpoet.ksp)
  implementation(libs.moshi.adapters)
  implementation(libs.moshi)
  implementation(project(":moshi-sealed:runtime"))

  runtimeOnly(libs.kotlin.metadata)

  testImplementation(libs.truth)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinCompileTesting)
  testImplementation(libs.kotlinCompileTesting.ksp)
  testRuntimeOnly(libs.ksp.aa.embeddable)
}
