// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.ir.compiler

import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.TestServices

fun TestConfigurationBuilder.configurePlugin() {
  useConfigurators(
    ::MoshiExtensionRegistrarConfigurator,
    ::MoshiRuntimeEnvironmentConfigurator,
  )

  useCustomRuntimeClasspathProviders(::MoshiRuntimeClassPathProvider)
}

class MoshiExtensionRegistrarConfigurator(testServices: TestServices) :
  EnvironmentConfigurator(testServices) {
  override fun CompilerPluginRegistrar.ExtensionStorage.registerCompilerExtensions(
    module: TestModule,
    configuration: CompilerConfiguration,
  ) {
    configuration.put(KEY_ENABLED, true)
    configuration.put(KEY_ENABLE_SEALED, true)
    with(MoshiComponentRegistrar()) { registerExtensions(configuration) }
  }
}
