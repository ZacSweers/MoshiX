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
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URL

buildscript {
  dependencies {
    classpath(kotlin("gradle-plugin", version = Dependencies.Kotlin.version))
  }
}

plugins {
  kotlin("jvm") version Dependencies.Kotlin.version apply false
  id("org.jetbrains.dokka") version Dependencies.Kotlin.dokkaVersion apply false
  id("com.vanniktech.maven.publish") version "0.13.0" apply false
}

subprojects {
  repositories {
    mavenCentral()
    google()
    // Required for Dokka
    exclusiveContent {
      forRepository {
        maven {
          name = "JCenter"
          setUrl("https://jcenter.bintray.com/")
        }
      }
      filter {
        includeModule("org.jetbrains.kotlinx", "kotlinx-html-jvm")
        includeGroup("org.jetbrains.dokka")
        includeModule("org.jetbrains", "markdown")
      }
    }
    // Kotlin EAPs, only tested on CI shadow jobs
    maven("https://dl.bintray.com/kotlin/kotlin-eap") {
      name = "Kotlin-eap"
      content {
        // this repository *only* contains Kotlin artifacts (don't try others here)
        includeGroupByRegex("org\\.jetbrains.*")
      }
    }
  }
  val toolChainVersion = project.findProperty("moshix.javaLanguageVersion")?.toString() ?: "8"
  val usePreview = project.hasProperty("moshix.javaPreview")
  pluginManager.withPlugin("java") {
    configure<JavaPluginExtension> {
      toolchain {
        languageVersion.set(JavaLanguageVersion.of(toolChainVersion))
      }
    }

    if (usePreview) {
      project.tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.add("--enable-preview")
      }

      project.tasks.withType<Test>().configureEach {
        // TODO why doesn't add() work?
        //  jvmArgs!!.add("--enable-preview")
        jvmArgs = listOf("--enable-preview")
      }
    }
  }
  pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    tasks.withType<KotlinCompile>().configureEach {
      kotlinOptions {
        jvmTarget = Dependencies.Kotlin.jvmTarget
        @Suppress("SuspiciousCollectionReassignment")
        freeCompilerArgs += Dependencies.Kotlin.defaultFreeCompilerArgs
      }
    }
    if (project.name != "sample" && !project.path.contains("sample")) {
      configure<KotlinProjectExtension> {
        explicitApi()
      }
    }
  }
  pluginManager.withPlugin("com.vanniktech.maven.publish") {
    apply(plugin = "org.jetbrains.dokka")
    tasks.named<DokkaTask>("dokkaHtml") {
      outputDirectory.set(rootProject.rootDir.resolve("docs/0.x"))
      dokkaSourceSets.configureEach {
        skipDeprecated.set(true)
        externalDocumentationLink {
          url.set(URL("https://square.github.io/okio/2.x/okio/"))
        }
        externalDocumentationLink {
          url.set(URL("https://square.github.io/moshi/1.x/moshi/"))
        }
      }
    }
  }
}
