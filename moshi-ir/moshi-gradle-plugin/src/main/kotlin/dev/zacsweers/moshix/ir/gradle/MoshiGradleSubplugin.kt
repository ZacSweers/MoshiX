// Copyright (C) 2021 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.ir.gradle

import com.google.devtools.ksp.gradle.KspExtension
import dev.zacsweers.moshi.ir.gradle.VERSION
import java.util.Locale.US
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class MoshiGradleSubplugin : KotlinCompilerPluginSupportPlugin {

  private companion object {
    val SUPPORTED_PLATFORMS = setOf(KotlinPlatformType.androidJvm, KotlinPlatformType.jvm)
    const val GENERATE_PROGUARD_RULES_KEY = "moshix.generateProguardRules"
  }

  override fun apply(target: Project) {
    target.extensions.create("moshi", MoshiPluginExtension::class.java)

    val localGradlePropertyProvider =
      target.createPropertiesProvider("gradle.properties").map {
        it.getProperty(GENERATE_PROGUARD_RULES_KEY)
      }

    if (
      localGradlePropertyProvider
        .orElse(target.providers.gradleProperty(GENERATE_PROGUARD_RULES_KEY))
        .orNull
        ?.toBoolean() != false
    ) {
      try {
        target.pluginManager.apply("com.google.devtools.ksp")
      } catch (e: Exception) {
        // KSP not on the classpath, ask them to add it
        error(
          "MoshiX proguard rule generation requires KSP to be applied to the project. " +
            "Please apply the KSP Gradle plugin ('com.google.devtools.ksp') to your buildscript and try again."
        )
      }

      fun addKspDep(configurationName: String) {
        target.dependencies.add(
          configurationName,
          "dev.zacsweers.moshix:moshi-proguard-rule-gen:$VERSION",
        )
      }

      // Add the KSP dependency to the appropriate configurations
      // In KMP, we only add to androidJvm/jvm targets
      val kExtension = target.kotlinExtension
      if (kExtension is KotlinMultiplatformExtension) {
        kExtension.targets
          .matching { it.platformType in SUPPORTED_PLATFORMS }
          .configureEach {
            addKspDep(
              "ksp${it.targetName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(US) else it.toString() }}"
            )
          }
      } else {
        addKspDep("ksp")
      }
      target.extensions.configure(KspExtension::class.java) {
        // Enable core moshi proguard rule gen
        it.arg("moshi.generateCoreMoshiProguardRules", "true")
        // Disable moshi's KSP codegen, we're doing it ourselves
        it.excludeProcessor(
          "com.squareup.moshi.kotlin.codegen.ksp.JsonClassSymbolProcessorProvider"
        )
      }
    }
  }

  override fun getCompilerPluginId(): String = "dev.zacsweers.moshix.compiler"

  override fun getPluginArtifact(): SubpluginArtifact =
    SubpluginArtifact(
      groupId = "dev.zacsweers.moshix",
      artifactId = "moshi-compiler-plugin",
      version = VERSION,
    )

  override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

  override fun applyToCompilation(
    kotlinCompilation: KotlinCompilation<*>
  ): Provider<List<SubpluginOption>> {
    val project = kotlinCompilation.target.project

    if (kotlinCompilation.platformType !in SUPPORTED_PLATFORMS) {
      return project.provider { emptyList() }
    }

    val extension = project.extensions.getByType(MoshiPluginExtension::class.java)

    val generatedAnnotation = extension.generatedAnnotation.orNull

    // Minimum Moshi version
    project.dependencies.add(
      kotlinCompilation.implementationConfigurationName,
      "com.squareup.moshi:moshi:1.13.0",
    )

    val enableSealed = extension.enableSealed.get()
    if (enableSealed) {
      project.dependencies.add(
        kotlinCompilation.implementationConfigurationName,
        "dev.zacsweers.moshix:moshi-sealed-runtime:$VERSION",
      )
    }

    return project.provider {
      buildList {
        add(SubpluginOption(key = "enabled", value = extension.enabled.get().toString()))
        add(SubpluginOption(key = "debug", value = extension.debug.get().toString()))
        add(SubpluginOption(key = "enableSealed", value = enableSealed.toString()))
        if (generatedAnnotation != null) {
          add(SubpluginOption(key = "generatedAnnotation", value = generatedAnnotation))
        }
      }
    }
  }
}

// Copied from kotlin-gradle-plugin, because they are internal.
internal inline fun <reified T : Task> Project.locateTask(name: String): TaskProvider<T>? =
  try {
    tasks.withType(T::class.java).named(name)
  } catch (e: UnknownTaskException) {
    null
  }
