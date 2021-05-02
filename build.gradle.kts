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

import com.diffplug.gradle.spotless.JavaExtension
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
  id("com.vanniktech.maven.publish") version "0.14.2" apply false
  id("com.diffplug.spotless") version "5.11.0"
  id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.5.0"
}

apiValidation {
  ignoredProjects.addAll(
    listOf(
      /* :moshi-ksp: */ "tests",
      /* :moshi-sealed: */ "sample",
      /* :moshi-sealed:sealed-interfaces-samples: */ "kotlin"
    )
  )
}

spotless {
  format("misc") {
    target("*.md", ".gitignore")
    trimTrailingWhitespace()
    indentWithSpaces(2)
    endWithNewline()
  }
  if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_15)) {
    val configureCommonJavaFormat: JavaExtension.() -> Unit = {
      googleJavaFormat("1.10.0")
    }
    java {
      configureCommonJavaFormat()
      target("**/*.java")
      targetExclude(
        "**/spotless.java",
        "**/build/**"
      )
      licenseHeaderFile("spotless/spotless.java")
    }
  }
  kotlin {
    ktlint(Dependencies.ktlintVersion).userData(mapOf("indent_size" to "2"))
    target("**/*.kt")
    trimTrailingWhitespace()
    endWithNewline()
    licenseHeaderFile("spotless/spotless.kt")
      .updateYearWithLatest(false)
    targetExclude(
      "**/Dependencies.kt", "**/spotless.kt", "**/build/**", "**/moshi-ksp/tests/**",
      "**/moshi-ksp/moshi-ksp/src/main/kotlin/dev/zacsweers/moshix/ksp/shade/**"
    )
  }
//  format("externalKotlin", KotlinExtension::class.java) {
//    // These don't use our spotless config for header files since we don't want to overwrite the
//    // existing copyright headers.
//    configureCommonKotlinFormat()
//  }
  kotlinGradle {
    ktlint(Dependencies.ktlintVersion).userData(mapOf("indent_size" to "2"))
    target("**/*.gradle.kts")
    trimTrailingWhitespace()
    endWithNewline()
    licenseHeaderFile("spotless/spotless.kts", "(import|plugins|buildscript|dependencies|pluginManagement)")
  }
}

subprojects {
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
        allWarningsAsErrors = true
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
