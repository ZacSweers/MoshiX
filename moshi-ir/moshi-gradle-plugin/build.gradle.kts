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
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.kotlinJvm)
  `java-gradle-plugin`
  alias(libs.plugins.dokka)
  alias(libs.plugins.mavenPublish)
  alias(libs.plugins.spotless)
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }

tasks.withType<JavaCompile>().configureEach {
  options.release.set(libs.versions.jvmTarget.map(String::toInt))
}

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

tasks.withType<KotlinCompile>().configureEach {
  dependsOn(copyVersionTemplatesProvider)
  compilerOptions {
    freeCompilerArgs.addAll("-opt-in=kotlin.ExperimentalStdlibApi")
    jvmTarget.set(libs.versions.jvmTarget.map(JvmTarget::fromTarget))
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

configure<MavenPublishBaseExtension> {
  // Can't do automatic release due to publishing both a plugin and regular artifacts
  publishToMavenCentral()
}

spotless {
  format("misc") {
    target("*.gradle", "*.md", ".gitignore")
    trimTrailingWhitespace()
    indentWithSpaces(2)
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
  compileOnly(libs.agp)
}
