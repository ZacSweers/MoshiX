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
import dev.zacsweers.moshix.ir.compiler.proguardgen.ProguardRuleGenerationExtension
import java.io.File
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension

@AutoService(CompilerPluginRegistrar::class)
public class MoshiComponentRegistrar : CompilerPluginRegistrar() {

  override val supportsK2: Boolean = true

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    if (configuration[KEY_ENABLED] == false) return
    val debug = configuration[KEY_DEBUG] ?: false
    val enableSealed = configuration[KEY_ENABLE_SEALED] ?: false
    val generateProguardRules = configuration[KEY_GENERATE_PROGUARD_RULES] ?: true

    val messageCollector =
      configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
    val fqGeneratedAnnotation = configuration[KEY_GENERATED_ANNOTATION]?.let(ClassId::fromString)

    if (generateProguardRules) {
      if (configuration.getBoolean(CommonConfigurationKeys.USE_FIR)) {
        configuration[CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY]?.report(
          CompilerMessageSeverity.ERROR,
          "moshi-ir's proguard rule generation currently doesn't support the experimental K2 compiler\nDisable the K2 compiler by removing -Xuse-k2 flag or disable proguard rule gen."
        )
        return
      }
      val resourceOutputDir =
        configuration.get(KEY_RESOURCES_OUTPUT_DIR)?.let(::File)
          ?: error("No resources dir provided for proguard rule generation")
      AnalysisHandlerExtension.registerExtension(
        ProguardRuleGenerationExtension(
          messageCollector = messageCollector,
          resourcesDir = resourceOutputDir,
          enableSealed = enableSealed,
          debug = debug
        )
      )
    }

    IrGenerationExtension.registerExtension(
      MoshiIrGenerationExtension(messageCollector, fqGeneratedAnnotation, enableSealed, debug)
    )
  }
}
