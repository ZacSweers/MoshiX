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

object Dependencies {

  const val autoCommon = "com.google.auto:auto-common:1.0"
  const val asm = "org.ow2.asm:asm:7.1"
  const val ktlintVersion = "0.41.0"

  object AutoService {
    const val annotations = "com.google.auto.service:auto-service-annotations:1.0"
    const val processor = "com.google.auto.service:auto-service:1.0"
    const val ksp = "dev.zacsweers.autoservice:auto-service-ksp:0.5.2"
  }

  object Incap {
    private const val version = "0.3"
    const val annotations = "net.ltgt.gradle.incap:incap:$version"
    const val processor = "net.ltgt.gradle.incap:incap-processor:$version"
  }

  object Kotlin {
    val version = System.getenv()["MOSHIX_KOTLIN"] ?: "1.5.20"
    const val dokkaVersion = "1.4.32"
    val reflect = "org.jetbrains.kotlin:kotlin-reflect:$version"
    const val metadata = "org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.3.0"
    val compilerEmbeddable = "org.jetbrains.kotlin:kotlin-compiler-embeddable:$version"
    const val jvmTarget = "1.8"
    val defaultFreeCompilerArgs = listOf("-Xjsr305=strict", "-progressive")

    object Ksp {
      const val version = "1.5.20-1.0.0-beta04"
      const val api = "com.google.devtools.ksp:symbol-processing-api:$version"
      const val ksp = "com.google.devtools.ksp:symbol-processing:$version"
    }
  }

  object KotlinPoet {
    private const val version = "1.9.0"
    const val kotlinPoet = "com.squareup:kotlinpoet:$version"
    const val metadata = "com.squareup:kotlinpoet-metadata-specs:$version"
    const val metadataSpecs = "com.squareup:kotlinpoet-metadata-specs:$version"
  }

  object Moshi {
    private const val version = "1.12.0"
    const val moshi = "com.squareup.moshi:moshi:$version"
    const val kotlin = "com.squareup.moshi:moshi-kotlin:$version"
    const val codegen = "com.squareup.moshi:moshi-kotlin-codegen:$version"
    const val adapters = "com.squareup.moshi:moshi-adapters:$version"
  }

  object OkHttp {
    private const val version = "4.9.1"
    const val okHttp = "com.squareup.okhttp3:okhttp:$version"
    const val mockWebServer = "com.squareup.okhttp3:mockwebserver:$version"
  }

  object Retrofit {
    private const val version = "2.9.0"
    const val retrofit = "com.squareup.retrofit2:retrofit:$version"
    const val moshiConverter = "com.squareup.retrofit2:converter-moshi:$version"
  }

  object Testing {
    const val compileTesting = "com.github.tschuchortdev:kotlin-compile-testing:1.4.2"
    const val kspCompileTesting = "com.github.tschuchortdev:kotlin-compile-testing-ksp:1.4.2"
    const val junit = "junit:junit:4.13.2"
    const val truth = "com.google.truth:truth:1.1.2"
  }
}
