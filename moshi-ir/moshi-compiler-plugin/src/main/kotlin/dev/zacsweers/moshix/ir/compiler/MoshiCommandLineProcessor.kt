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
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

internal val KEY_ENABLED =
  CompilerConfigurationKey<Boolean>("Enable/disable MoshiX's plugins on the given compilation")
internal val KEY_DEBUG =
  CompilerConfigurationKey<Boolean>("Enable/disable debug logging on the given compilation")
internal val KEY_GENERATED_ANNOTATION =
  CompilerConfigurationKey<String>(
    "The FQCN to a generated (i.e. javax/annotation/processing/Generated) annotation to include on generated code"
  )
internal val KEY_ENABLE_SEALED =
  CompilerConfigurationKey<Boolean>("Enable/disable moshi-sealed support in code generation")
internal val KEY_GENERATE_PROGUARD_RULES =
  CompilerConfigurationKey<Boolean>(
    "Enable/disable proguard rule generation in code gen. Implemented as an AnalysisHandlerExtension"
  )
internal val KEY_RESOURCES_OUTPUT_DIR =
  CompilerConfigurationKey<String>(
    "The output directory for generated proguard rules, only applicable if generateProguardRules is enabled"
  )

@AutoService(CommandLineProcessor::class)
public class MoshiCommandLineProcessor : CommandLineProcessor {
  internal companion object {
    val OPTION_ENABLED =
      CliOption(
        optionName = "enabled",
        valueDescription = "<true | false>",
        description = KEY_ENABLED.toString(),
        required = true,
        allowMultipleOccurrences = false
      )
    val OPTION_DEBUG =
      CliOption(
        optionName = "debug",
        valueDescription = "<true | false>",
        description = KEY_DEBUG.toString(),
        required = false,
        allowMultipleOccurrences = false
      )
    val OPTION_ENABLE_SEALED =
      CliOption(
        optionName = "enableSealed",
        valueDescription = "<true | false>",
        description = KEY_GENERATED_ANNOTATION.toString(),
        required = false,
        allowMultipleOccurrences = false
      )
    val OPTION_GENERATED_ANNOTATION =
      CliOption(
        optionName = "generatedAnnotation",
        valueDescription = "String",
        description = KEY_ENABLE_SEALED.toString(),
        required = false,
        allowMultipleOccurrences = false
      )
    val OPTION_GENERATE_PROGUARD_RULES =
      CliOption(
        optionName = "generateProguardRules",
        valueDescription = "<true | false>",
        description = KEY_GENERATE_PROGUARD_RULES.toString(),
        required = false,
        allowMultipleOccurrences = false
      )
    val OPTION_RESOURCES_OUTPUT_DIR =
      CliOption(
        optionName = "resourcesOutputDir",
        valueDescription = "String",
        description = KEY_RESOURCES_OUTPUT_DIR.toString(),
        required = false,
        allowMultipleOccurrences = false
      )
  }

  override val pluginId: String = "dev.zacsweers.moshix.compiler"

  override val pluginOptions: Collection<AbstractCliOption> =
    listOf(
      OPTION_DEBUG,
      OPTION_ENABLED,
      OPTION_ENABLE_SEALED,
      OPTION_RESOURCES_OUTPUT_DIR,
      OPTION_GENERATED_ANNOTATION,
      OPTION_GENERATE_PROGUARD_RULES
    )

  override fun processOption(
    option: AbstractCliOption,
    value: String,
    configuration: CompilerConfiguration
  ): Unit =
    when (option.optionName) {
      "enabled" -> configuration.put(KEY_ENABLED, value.toBoolean())
      "debug" -> configuration.put(KEY_DEBUG, value.toBoolean())
      "enableSealed" -> configuration.put(KEY_ENABLE_SEALED, value.toBoolean())
      "generatedAnnotation" -> configuration.put(KEY_GENERATED_ANNOTATION, value)
      "generateProguardRules" -> configuration.put(KEY_GENERATE_PROGUARD_RULES, value.toBoolean())
      "resourcesOutputDir" -> configuration.put(KEY_RESOURCES_OUTPUT_DIR, value)
      else -> error("Unknown plugin option: ${option.optionName}")
    }
}
