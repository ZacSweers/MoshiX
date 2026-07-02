// Copyright (C) 2021 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.ir.gradle

import dev.zacsweers.moshi.ir.gradle.VERSION
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class MoshiGradleSubplugin : KotlinCompilerPluginSupportPlugin {

  private companion object {
    const val COMPILER_PLUGIN_ID = "dev.zacsweers.moshix.compiler"
    const val KEY_ENABLED = "enabled"
    const val KEY_SEALED = "enableSealed"
    const val KEY_GENERATED_ANNOTATION = "generatedAnnotation"

    val SUPPORTED_PLATFORMS = setOf(KotlinPlatformType.androidJvm, KotlinPlatformType.jvm)
  }

  override fun apply(target: Project) {
    target.extensions.create("moshi", MoshiPluginExtension::class.java)
  }

  override fun getCompilerPluginId(): String = COMPILER_PLUGIN_ID

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
    val applyMoshiDependency = extension.applyMoshiDependency.get()
    if (applyMoshiDependency) {
      project.dependencies.add(
        kotlinCompilation.defaultSourceSet.implementationConfigurationName,
        "com.squareup.moshi:moshi:1.13.0",
      )
    }

    val applyMoshiXDependency = extension.applyMoshiXDependency.get()
    if (applyMoshiXDependency) {
      project.dependencies.add(
        kotlinCompilation.defaultSourceSet.implementationConfigurationName,
        "dev.zacsweers.moshix:moshix-runtime:$VERSION",
      )
    }

    val enableSealed = extension.enableSealed.get()
    if (enableSealed) {
      project.dependencies.add(
        kotlinCompilation.defaultSourceSet.implementationConfigurationName,
        "dev.zacsweers.moshix:moshi-sealed-runtime:$VERSION",
      )
    }

    return project.provider {
      buildList {
        add(SubpluginOption(key = KEY_ENABLED, value = extension.enabled.get().toString()))
        add(SubpluginOption(key = KEY_SEALED, value = enableSealed.toString()))
        if (generatedAnnotation != null) {
          add(SubpluginOption(key = KEY_GENERATED_ANNOTATION, value = generatedAnnotation))
        }
      }
    }
  }
}
