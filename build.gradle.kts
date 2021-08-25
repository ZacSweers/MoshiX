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

import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URL

buildscript {
  dependencies {
    classpath(kotlin("gradle-plugin", version = (System.getenv()["MOSHIX_KOTLIN"] ?: "1.5.30")))
  }
}

plugins {
  kotlin("jvm") version (System.getenv()["MOSHIX_KOTLIN"] ?: "1.5.30") apply false
  alias(libs.plugins.dokka) apply false
  alias(libs.plugins.mavenPublish) apply false
  alias(libs.plugins.spotless)
  alias(libs.plugins.kotlinBinaryCompatibilityValidator)
}

apiValidation {
  ignoredProjects.addAll(
    listOf(
      /* :moshi-ksp: */ "extra-moshi-test-module",
      /* :moshi-ksp: */ "tests",
      /* :moshi-sealed: */ "sample"
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
  java {
    googleJavaFormat(libs.versions.gjf.get())
    target("**/*.java")
    targetExclude(
      "**/spotless.java",
      "**/build/**"
    )
    licenseHeaderFile("spotless/spotless.java")
  }
  kotlin {
    ktlint(libs.versions.ktlint.get()).userData(mapOf("indent_size" to "2"))
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
    ktlint(libs.versions.ktlint.get()).userData(mapOf("indent_size" to "2"))
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
  val release = toolChainVersion.toInt()
  val usePreview = project.hasProperty("moshix.javaPreview")
  pluginManager.withPlugin("java") {
    configure<JavaPluginExtension> {
      toolchain {
        languageVersion.set(JavaLanguageVersion.of(toolChainVersion))
      }
    }

    project.tasks.withType<JavaCompile>().configureEach {
      options.release.set(release)
      if (usePreview) {
        options.compilerArgs.add("--enable-preview")
      }
    }
    if (usePreview) {
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
        jvmTarget = libs.versions.jvmTarget.get()
        @Suppress("SuspiciousCollectionReassignment")
        freeCompilerArgs += listOf("-Xjsr305=strict", "-progressive", "-Xopt-in=kotlin.RequiresOptIn")
        // TODO disabled because Gradle's Kotlin handling is silly
        //  https://github.com/gradle/gradle/issues/16779
//        allWarningsAsErrors = true
      }
    }
    if (project.name != "sample" && !project.path.contains("sample") && !project.path.contains("test")) {
      configure<KotlinProjectExtension> {
        explicitApi()
        kotlinDaemonJvmArgs = listOf(
          "--add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
          "--add-opens=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
          "--add-opens=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
          "--add-opens=jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED ",
          "--add-opens=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
          "--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
          "--add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
          "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
          "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
          "--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
          "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
          "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
        )
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
  // configuration required to produce unique META-INF/*.kotlin_module file names
  tasks.withType<KotlinCompile> {
    kotlinOptions {
      if (project.hasProperty("POM_ARTIFACT_ID")) {
        moduleName = project.property("POM_ARTIFACT_ID") as String
      }
    }
  }
}
