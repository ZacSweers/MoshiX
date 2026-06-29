// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.ir.compiler

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.name.ClassId

@AutoService(CompilerPluginRegistrar::class)
public class MoshiComponentRegistrar : CompilerPluginRegistrar() {

  override val pluginId: String = "dev.zacsweers.moshix.compiler"

  override val supportsK2: Boolean = true

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    if (configuration[KEY_ENABLED] == false) return
    val debug = configuration[KEY_DEBUG] == true
    val enableSealed = configuration[KEY_ENABLE_SEALED] == true

    val messageCollector =
      configuration.get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
    val fqGeneratedAnnotation = configuration[KEY_GENERATED_ANNOTATION]?.let(ClassId::fromString)

    IrGenerationExtension.registerExtension(
      MoshiIrGenerationExtension(messageCollector, fqGeneratedAnnotation, enableSealed, debug)
    )
  }
}
