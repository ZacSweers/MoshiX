// Copyright (C) 2021 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.ir.gradle

import com.google.devtools.ksp.gradle.KspExtension
import dev.zacsweers.moshi.ir.gradle.VERSION
import dev.zacsweers.moshix.ir.gradle.MoshiGradleSubplugin.Companion.SUPPORTED_PLATFORMS
import java.util.Locale.US
import javax.inject.Inject
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension

abstract class MoshiPluginExtension
@Inject
constructor(objects: ObjectFactory, providers: ProviderFactory) {
  val enabled: Property<Boolean> = objects.property(Boolean::class.javaObjectType).convention(true)
  /** Enables debug logging. Useful mostly for helping report bugs/issues. */
  val debug: Property<Boolean> = objects.property(Boolean::class.javaObjectType).convention(false)
  /**
   * Define a custom generated annotation.
   *
   * Note that this must be in the format of a string where packages are delimited by '/' and
   * classes by '.', e.g. "kotlin/Map.Entry"
   *
   * **Note:** this is not currently implemented yet
   */
  val generatedAnnotation: Property<String> = objects.property(String::class.java)
  /** Enables moshi-sealed code gen. Disabled by default. */
  val enableSealed: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

  /**
   * Enables generation of proguard rules for adapters via KSP. Enabled by default and configurable
   * via the `moshix.generateProguardRules` Gradle property.
   */
  val generateProguardRules: Property<Boolean> =
    objects
      .property(Boolean::class.java)
      .convention(
        providers
          .gradleProperty("moshix.generateProguardRules")
          .map { it.toBooleanStrictOrNull() ?: true }
          .orElse(true)
      )

  internal fun apply(target: Project) {
    if (generateProguardRules.get()) {
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
}
