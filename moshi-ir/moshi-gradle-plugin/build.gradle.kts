// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.kotlinJvm)
  `java-gradle-plugin`
  alias(libs.plugins.dokka)
  alias(libs.plugins.mavenPublish)
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
    languageVersion.set(KotlinVersion.KOTLIN_2_0)
    apiVersion.set(KotlinVersion.KOTLIN_2_0)
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

dependencies {
  compileOnly(libs.kotlin.gradlePlugin)
  compileOnly(libs.kotlin.gradlePlugin.api)
  compileOnly(libs.kotlin.stdlib)
  compileOnly(libs.ksp.gradlePlugin)
  compileOnly(libs.agp)
}
