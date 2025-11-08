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
import com.android.build.api.dsl.Lint
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.kotlinJvm) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.dokka)
  alias(libs.plugins.mavenPublish) apply false
  alias(libs.plugins.spotless)
  alias(libs.plugins.kotlinBinaryCompatibilityValidator)
  alias(libs.plugins.lint) apply false
  alias(libs.plugins.moshix) apply false
}

apiValidation {
  ignoredProjects +=
    listOf(
      /* :moshi-ir: */
      "moshi-kotlin-tests",
      "extra-moshi-test-module",
      /* :moshi-sealed: */
      "sample",
    )
}

dokka {
  dokkaPublications.html {
    outputDirectory.set(rootDir.resolve("docs/api/0.x"))
    includes.from(project.layout.projectDirectory.file("README.md"))
  }
}

val ktfmtVersion = libs.versions.ktfmt.get()

spotless {
  format("misc") {
    target("*.md", ".gitignore")
    trimTrailingWhitespace()
    leadingTabsToSpaces(2)
    endWithNewline()
  }
  java {
    googleJavaFormat(libs.versions.gjf.get())
    target("**/*.java")
    targetExclude("**/spotless.java", "**/build/**", "**/.gradle/**")
  }
  kotlin {
    ktfmt(ktfmtVersion).googleStyle()
    target("**/*.kt")
    trimTrailingWhitespace()
    endWithNewline()
    targetExclude("**/Dependencies.kt", "**/spotless.kt", "**/build/**")
  }
  kotlinGradle {
    ktfmt(ktfmtVersion).googleStyle()
    target("**/*.gradle.kts")
    trimTrailingWhitespace()
    endWithNewline()
  }
}

subprojects {
  pluginManager.withPlugin("java") {
    // javaReleaseVersion can be set to override the global version
    // Can't use providers.gradleProperty() because it doesn't work on subprojects
    val jvmTargetProvider =
      provider<String> { findProperty("moshix.javaReleaseVersion") as? String? }
        .orElse(libs.versions.jvmTarget)
        .map(String::toInt)
    configure<JavaPluginExtension> {
      toolchain { languageVersion.set(JavaLanguageVersion.of(libs.versions.jdk.get().toInt())) }
    }
    project.tasks.withType<JavaCompile>().configureEach { options.release.set(jvmTargetProvider) }
  }
  pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    tasks.withType<KotlinCompile>().configureEach {
      compilerOptions {
        jvmTarget.set(libs.versions.jvmTarget.map(JvmTarget::fromTarget))
        freeCompilerArgs.addAll("-Xjsr305=strict")
        progressiveMode.set(true)
        // TODO disabled because Gradle's Kotlin handling is silly
        //  https://github.com/gradle/gradle/issues/16779
        //  allWarningsAsErrors = true
      }
    }
    if (
      project.name != "sample" && !project.path.contains("sample") && !project.path.contains("test")
    ) {
      configure<KotlinProjectExtension> { explicitApi() }
    }
  }
  pluginManager.withPlugin("com.vanniktech.maven.publish") {
    apply(plugin = "org.jetbrains.dokka")
    configure<DokkaExtension> {
      basePublicationsDirectory.set(layout.buildDirectory.dir("dokkaDir"))
      dokkaSourceSets.configureEach {
        skipDeprecated.set(true)
        externalDocumentationLinks {
          register("okio") { url("https://square.github.io/okio/2.x/okio/") }
          register("moshi") { url("https://square.github.io/moshi/1.x/moshi/") }
        }
      }
    }
    configure<MavenPublishBaseExtension> { publishToMavenCentral(automaticRelease = true) }

    // configuration required to produce unique META-INF/*.kotlin_module file names
    tasks.withType<KotlinCompile>().configureEach {
      compilerOptions { moduleName.set(project.property("POM_ARTIFACT_ID") as String) }
    }
  }

  pluginManager.withPlugin("com.android.lint") {
    configure<Lint> {
      fatal += "KotlincFE10"
      disable += "UnknownIssueId"
      baseline = project.layout.projectDirectory.file("lint-baseline.xml").asFile
    }
  }
}

dependencies {
  dokka(projects.moshiAdapters)
  dokka(projects.moshiImmutableAdapters)
  dokka(projects.moshiIr.moshiCompilerPlugin)
  dokka(projects.moshiMetadataReflect)
  dokka(projects.moshiProguardRuleGen)
  dokka(projects.moshiSealed.codegen)
  dokka(projects.moshiSealed.javaSealedReflect)
  dokka(projects.moshiSealed.metadataReflect)
  dokka(projects.moshiSealed.reflect)
  dokka(projects.moshiSealed.runtime)
}
