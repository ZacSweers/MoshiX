// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.ir.compiler

import com.google.auto.service.AutoService
import dev.zacsweers.metro.compiler.compat.CompatContext
import dev.zacsweers.moshix.ir.compiler.fir.MoshiFirExtensionRegistrar
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.name.ClassId

@AutoService(CompilerPluginRegistrar::class)
public class MoshiComponentRegistrar : CompilerPluginRegistrar() {

  override val pluginId: String = "dev.zacsweers.moshix.compiler"

  override val supportsK2: Boolean = true

  @OptIn(ExperimentalCompilerApi::class)
  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    if (configuration[KEY_ENABLED] == false) return
    val enableSealed = configuration[KEY_ENABLE_SEALED] == true

    val fqGeneratedAnnotation = configuration[KEY_GENERATED_ANNOTATION]?.let(ClassId::fromString)
    val compatContext =
      try {
        CompatContext.create()
      } catch (t: Throwable) {
        System.err.println(
          "[MoshiX] Skipping enabling MoshiX extensions, unable to create CompatContext"
        )
        t.printStackTrace()
        return
      }

    with(compatContext) {
      registerFirExtensionCompat(MoshiFirExtensionRegistrar(enableSealed, compatContext))
      registerIrExtensionCompat(
        MoshiIrGenerationExtension(fqGeneratedAnnotation, enableSealed, compatContext)
      )
    }
  }
}
