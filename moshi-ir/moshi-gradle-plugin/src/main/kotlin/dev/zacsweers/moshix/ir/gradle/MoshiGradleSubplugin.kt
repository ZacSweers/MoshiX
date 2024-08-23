// Copyright (C) 2021 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.ir.gradle

import dev.zacsweers.moshi.ir.gradle.VERSION
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class MoshiGradleSubplugin : KotlinCompilerPluginSupportPlugin {

  internal companion object {
    val SUPPORTED_PLATFORMS = setOf(KotlinPlatformType.androidJvm, KotlinPlatformType.jvm)
  }

  override fun apply(target: Project) {
    val extension = target.extensions.create("moshi", MoshiPluginExtension::class.java)
    target.afterEvaluate { extension.apply(target) }
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
