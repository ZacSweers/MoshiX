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

internal val KEY_ENABLED = CompilerConfigurationKey<Boolean>("enabled")
internal val KEY_GENERATED_ANNOTATION = CompilerConfigurationKey<String>("generatedAnnotation")

@AutoService(CommandLineProcessor::class)
public class MoshiCommandLineProcessor : CommandLineProcessor {

  override val pluginId: String = "moshi-compiler-plugin"

  override val pluginOptions: Collection<AbstractCliOption> =
      listOf(
          CliOption("enabled", "<true | false>", "", required = true),
          CliOption("generatedAnnotation", "String", "", required = false),
      )

  override fun processOption(
      option: AbstractCliOption,
      value: String,
      configuration: CompilerConfiguration
  ): Unit =
      when (option.optionName) {
        "enabled" -> configuration.put(KEY_ENABLED, value.toBoolean())
        "generatedAnnotation" -> configuration.put(KEY_GENERATED_ANNOTATION, value)
        else -> error("Unknown plugin option: ${option.optionName}")
      }
}
