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
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.com.intellij.openapi.extensions.Extensions
import org.jetbrains.kotlin.com.intellij.openapi.extensions.LoadingOrder
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension

@AutoService(ComponentRegistrar::class)
public class MoshiComponentRegistrar : ComponentRegistrar {

  override fun registerProjectComponents(
      project: MockProject,
      configuration: CompilerConfiguration
  ) {

    if (configuration[KEY_ENABLED] == false) return
    val debug = configuration[KEY_DEBUG] ?: false
    val enableSealed = configuration[KEY_ENABLE_SEALED] ?: false
    val generateProguardRules = configuration[KEY_GENERATE_PROGUARD_RULES] ?: true

    val messageCollector =
        configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
    val fqGeneratedAnnotation = configuration[KEY_GENERATED_ANNOTATION]?.let(::FqName)

    if (generateProguardRules) {
      val resourceOutputDir =
          configuration.get(KEY_RESOURCES_OUTPUT_DIR)?.let(::File)
              ?: error("No resources dir provided for proguard rule generation")
      // It's important to register our extension at the first position. The compiler calls each
      // extension one by one. If an extension returns a result, then the compiler won't call any
      // other extension. That usually happens with Kapt in the stub generating task.
      //
      // It's not dangerous for our extension to run first, because we generate code, restart the
      // analysis phase and then don't return a result anymore. That means the next extension can
      // take over. If we wouldn't do this and any other extension won't let ours run, then we
      // couldn't generate any code.
      AnalysisHandlerExtension.registerExtensionFirst(
          project,
          ProguardRuleGenerationExtension(
              messageCollector = messageCollector,
              resourcesDir = resourceOutputDir,
              enableSealed = enableSealed,
              debug = debug))
    }

    IrGenerationExtension.registerExtension(
        project,
        MoshiIrGenerationExtension(messageCollector, fqGeneratedAnnotation, enableSealed, debug))
  }
}

private fun AnalysisHandlerExtension.Companion.registerExtensionFirst(
    project: MockProject,
    extension: AnalysisHandlerExtension
) {
  // See
  // https://github.com/detekt/detekt/commit/a0d36e2ca4f6ca38cac0f9cb418df989ccf4f063#diff-1abfa33e705e9d1a139e397920c0a3b91cff3fe0d738291dd9bce517943290d0R24-R28
  @Suppress("DEPRECATION")
  synchronized(Extensions.getRootArea()) {
    project
        .extensionArea
        .getExtensionPoint(AnalysisHandlerExtension.extensionPointName)
        .registerExtension(extension, LoadingOrder.FIRST, project)
  }
}
