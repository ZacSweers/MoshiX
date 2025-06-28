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

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.kotlinJvm)
  `java-gradle-plugin`
  alias(libs.plugins.dokka)
  alias(libs.plugins.mavenPublish)
  alias(libs.plugins.spotless)
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(libs.versions.jdk.get().toInt())) } }

tasks.withType<JavaCompile>().configureEach {
  options.release.set(libs.versions.jvmTarget.map(String::toInt))
}

// region Version.kt template for setting the project version in the build
sourceSets {
  main {
    java.srcDir(layout.buildDirectory.file("generated/sources/version-templates/kotlin/main"))
  }
}

val copyVersionTemplatesProvider =
  tasks.register<Copy>("copyVersionTemplates") {
    inputs.property("version", project.property("VERSION_NAME"))
    from(project.layout.projectDirectory.dir("version-templates"))
    into(project.layout.buildDirectory.dir("generated/sources/version-templates/kotlin/main"))
    expand(mapOf("projectVersion" to "${project.property("VERSION_NAME")}"))
    filteringCharset = "UTF-8"
  }

// endregion

tasks.withType<KotlinCompile>().configureEach {
  dependsOn(copyVersionTemplatesProvider)
  compilerOptions {
    freeCompilerArgs.addAll("-opt-in=kotlin.ExperimentalStdlibApi")
    jvmTarget.set(libs.versions.jvmTarget.map(JvmTarget::fromTarget))

    // Enforce lower target Kotlin version for Gradle compat
    @Suppress("DEPRECATION")
    languageVersion.set(KotlinVersion.KOTLIN_1_9)
    apiVersion.set(KotlinVersion.KOTLIN_1_9)
  }
}

tasks
  .matching { it.name == "sourcesJar" || it.name == "dokkaGeneratePublicationHtml" }
  .configureEach { dependsOn(copyVersionTemplatesProvider) }

gradlePlugin {
  website = providers.gradleProperty("POM_URL").get()
  vcsUrl = providers.gradleProperty("POM_SCM_URL").get()

  plugins {
    register("moshiPlugin") {
      id = "dev.zacsweers.moshix"
      displayName = providers.gradleProperty("POM_NAME").get()
      implementationClass = "dev.zacsweers.moshix.ir.gradle.MoshiGradleSubplugin"
      description = providers.gradleProperty("POM_DESCRIPTION").get()
    }
  }
}

dokka {
  dokkaPublications.html {
    outputDirectory.set(rootDir.resolve("docs/api/0.x"))
    includes.from(project.layout.projectDirectory.file("README.md"))
  }
  dokkaSourceSets.configureEach { skipDeprecated.set(true) }
}

configure<MavenPublishBaseExtension> { publishToMavenCentral(automaticRelease = true) }

spotless {
  format("misc") {
    target("*.gradle", "*.md", ".gitignore")
    trimTrailingWhitespace()
    leadingTabsToSpaces(2)
    endWithNewline()
  }
  kotlin {
    target("src/**/*.kt")
    ktfmt(libs.versions.ktfmt.get()).googleStyle()
    trimTrailingWhitespace()
    endWithNewline()
    licenseHeaderFile("../../spotless/spotless.kt")
    targetExclude("**/spotless.kt", "build/**")
  }
}

dependencies {
  compileOnly(libs.kotlin.gradlePlugin)
  compileOnly(libs.kotlin.gradlePlugin.api)
  compileOnly(libs.kotlin.stdlib)
  compileOnly(libs.ksp.gradlePlugin)
  compileOnly(libs.agp)
}
