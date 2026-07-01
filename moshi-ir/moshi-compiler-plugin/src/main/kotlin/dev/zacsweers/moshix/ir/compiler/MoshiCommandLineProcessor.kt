// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.ir.compiler

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

internal const val OPTION_ENABLED_NAME = "enabled"
internal const val OPTION_ENABLE_SEALED_NAME = "enableSealed"
internal const val OPTION_GENERATED_ANNOTATION_NAME = "generatedAnnotation"

internal val KEY_ENABLED =
  CompilerConfigurationKey<Boolean>("Enable/disable MoshiX's plugins on the given compilation")
internal val KEY_GENERATED_ANNOTATION =
  CompilerConfigurationKey<String>(
    "The FQCN to a generated (i.e. javax/annotation/processing/Generated) annotation to include on generated code"
  )
internal val KEY_ENABLE_SEALED =
  CompilerConfigurationKey<Boolean>("Enable/disable moshi-sealed support in code generation")

@AutoService(CommandLineProcessor::class)
public class MoshiCommandLineProcessor : CommandLineProcessor {
  internal companion object {
    val OPTION_ENABLED =
      CliOption(
        optionName = OPTION_ENABLED_NAME,
        valueDescription = "<true | false>",
        description = KEY_ENABLED.toString(),
        required = true,
        allowMultipleOccurrences = false,
      )
    val OPTION_ENABLE_SEALED =
      CliOption(
        optionName = OPTION_ENABLE_SEALED_NAME,
        valueDescription = "<true | false>",
        description = KEY_ENABLE_SEALED.toString(),
        required = false,
        allowMultipleOccurrences = false,
      )
    val OPTION_GENERATED_ANNOTATION =
      CliOption(
        optionName = OPTION_GENERATED_ANNOTATION_NAME,
        valueDescription = "String",
        description = KEY_GENERATED_ANNOTATION.toString(),
        required = false,
        allowMultipleOccurrences = false,
      )
  }

  override val pluginId: String = "dev.zacsweers.moshix.compiler"

  override val pluginOptions: Collection<AbstractCliOption> =
    listOf(OPTION_ENABLED, OPTION_ENABLE_SEALED, OPTION_GENERATED_ANNOTATION)

  override fun processOption(
    option: AbstractCliOption,
    value: String,
    configuration: CompilerConfiguration,
  ): Unit =
    when (option.optionName) {
      OPTION_ENABLED_NAME -> configuration.put(KEY_ENABLED, value.toBoolean())
      OPTION_ENABLE_SEALED_NAME -> configuration.put(KEY_ENABLE_SEALED, value.toBoolean())
      OPTION_GENERATED_ANNOTATION_NAME -> configuration.put(KEY_GENERATED_ANNOTATION, value)
      else -> error("Unknown plugin option: ${option.optionName}")
    }
}
