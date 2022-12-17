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
package dev.zacsweers.moshix.ir.gradle

import dev.zacsweers.moshi.ir.gradle.VERSION
import java.io.File
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.plugin.FilesSubpluginOption
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilationWithResources
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAndroidCompilation

class MoshiGradleSubplugin : KotlinCompilerPluginSupportPlugin {

  companion object {
    @JvmStatic
    fun getMoshiXOutputDir(project: Project, sourceSetName: String) =
      File(project.project.buildDir, "generated/moshix/$sourceSetName")

    @JvmStatic
    fun getMoshiXResourceOutputDir(project: Project, sourceSetName: String) =
      File(getMoshiXOutputDir(project, sourceSetName), "resources")
  }

  override fun apply(target: Project) {
    target.extensions.create("moshi", MoshiPluginExtension::class.java)
  }

  override fun getCompilerPluginId(): String = "dev.zacsweers.moshix.compiler"

  override fun getPluginArtifact(): SubpluginArtifact =
    SubpluginArtifact(
      groupId = "dev.zacsweers.moshix",
      artifactId = "moshi-compiler-plugin",
      version = VERSION
    )

  override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

  override fun applyToCompilation(
    kotlinCompilation: KotlinCompilation<*>
  ): Provider<List<SubpluginOption>> {
    val project = kotlinCompilation.target.project
    val extension = project.extensions.getByType(MoshiPluginExtension::class.java)
    val generatedAnnotation = extension.generatedAnnotation.orNull
    val generateProguardRules = extension.generateProguardRules.get()
    val sourceSetName = kotlinCompilation.compilationName

    // Minimum Moshi version
    project.dependencies.add("implementation", "com.squareup.moshi:moshi:1.13.0")

    val enableSealed = extension.enableSealed.get()
    if (enableSealed) {
      project.dependencies.add(
        "implementation",
        "dev.zacsweers.moshix:moshi-sealed-runtime:$VERSION"
      )
    }

    if (generateProguardRules) {
      val resourceOutputDir = getMoshiXResourceOutputDir(project, sourceSetName)
      val compilationTask = kotlinCompilation.compileTaskProvider
      compilationTask.configure { it.outputs.dirs(resourceOutputDir) }
      val processResourcesTaskName =
        (kotlinCompilation as? KotlinCompilationWithResources)?.processResourcesTaskName
          ?: "processResources"
      project.locateTask<ProcessResources>(processResourcesTaskName)?.let { provider ->
        provider.configure { resourcesTask ->
          resourcesTask.dependsOn(compilationTask)
          resourcesTask.from(resourceOutputDir)
        }
      }
      if (kotlinCompilation is KotlinJvmAndroidCompilation) {
        kotlinCompilation.androidVariant.registerPostJavacGeneratedBytecode(
          project.files(resourceOutputDir)
        )
      }
    }

    return project.provider {
      buildList {
        add(SubpluginOption(key = "enabled", value = extension.enabled.get().toString()))
        add(SubpluginOption(key = "debug", value = extension.debug.get().toString()))
        add(SubpluginOption(key = "enableSealed", value = enableSealed.toString()))
        if (generatedAnnotation != null) {
          add(SubpluginOption(key = "generatedAnnotation", value = generatedAnnotation))
        }
        add(SubpluginOption("generateProguardRules", generateProguardRules.toString()))
        if (generateProguardRules) {
          val resourceOutputDir = getMoshiXResourceOutputDir(project, sourceSetName)
          add(FilesSubpluginOption("resourcesOutputDir", listOf(resourceOutputDir)))
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
