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

  public val pluginId: String = "dev.zacsweers.moshix.compiler"

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
