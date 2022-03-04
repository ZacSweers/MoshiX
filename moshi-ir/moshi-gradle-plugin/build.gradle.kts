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

import org.jetbrains.dokka.gradle.DokkaTask

plugins {
  id("org.jetbrains.kotlin.jvm") version "1.6.10"
  id("java-gradle-plugin")
  id("org.jetbrains.dokka") version "1.6.10"
  id("com.vanniktech.maven.publish") version "0.19.0"
  id("com.diffplug.spotless") version "6.3.0"
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }

tasks.withType<JavaCompile>().configureEach { options.release.set(8) }

// region Version.kt template for setting the project version in the build
sourceSets { main { java.srcDir("$buildDir/generated/sources/version-templates/kotlin/main") } }

val copyVersionTemplatesProvider =
    tasks.register<Copy>("copyVersionTemplates") {
      inputs.property("version", project.property("VERSION_NAME"))
      from(project.layout.projectDirectory.dir("version-templates"))
      into(project.layout.buildDirectory.dir("generated/sources/version-templates/kotlin/main"))
      expand(mapOf("projectVersion" to "${project.property("VERSION_NAME")}"))
      filteringCharset = "UTF-8"
    }
// endregion

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  dependsOn(copyVersionTemplatesProvider)
  kotlinOptions {
    @Suppress("SuspiciousCollectionReassignment")
    freeCompilerArgs +=
        listOf("-opt-in=kotlin.RequiresOptIn", "-opt-in=kotlin.ExperimentalStdlibApi")
    jvmTarget = "1.8"
  }
}

gradlePlugin {
  plugins {
    create("moshiPlugin") {
      id = "dev.zacsweers.moshix"
      implementationClass = "dev.zacsweers.moshix.ir.gradle.MoshiGradleSubplugin"
    }
  }
}

tasks.named<DokkaTask>("dokkaHtml") {
  outputDirectory.set(rootProject.file("../docs/0.x"))
  dokkaSourceSets.configureEach { skipDeprecated.set(true) }
}

repositories {
  mavenCentral()
  google()
}

spotless {
  format("misc") {
    target("*.gradle", "*.md", ".gitignore")
    trimTrailingWhitespace()
    indentWithSpaces(2)
    endWithNewline()
  }
  kotlin {
    target("**/*.kt")
    ktfmt("0.30")
    trimTrailingWhitespace()
    endWithNewline()
    licenseHeaderFile("../spotless/spotless.kt")
    targetExclude("**/spotless.kt", "build/**")
  }
}

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin-api:1.6.10")
  compileOnly("com.android.tools.build:gradle:7.1.2")
  compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.10")
  implementation("com.google.auto.service:auto-service-annotations:1.0.1")
}
