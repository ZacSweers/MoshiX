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
  id("dev.zacsweers.autoservice.ksp") version "0.2.0-SNAPSHOT" apply false
}

subprojects {
  repositories {
    mavenCentral()
    google()
    maven("https://oss.sonatype.org/content/repositories/snapshots")
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
  }
  pluginManager.withPlugin("java") {
    configure<JavaPluginExtension> {
      sourceCompatibility = JavaVersion.VERSION_1_8
      targetCompatibility = JavaVersion.VERSION_1_8
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
    if (project.name != "sample") {
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
