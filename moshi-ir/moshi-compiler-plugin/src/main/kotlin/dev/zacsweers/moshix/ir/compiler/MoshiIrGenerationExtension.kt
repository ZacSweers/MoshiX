// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.ir.compiler

import dev.zacsweers.metro.compiler.compat.CompatContext
import dev.zacsweers.moshix.ir.compiler.util.error
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.name.ClassId

internal class MoshiIrGenerationExtension(
  private val generatedAnnotationName: ClassId?,
  private val enableSealed: Boolean,
  private val compatContext: CompatContext,
) : IrGenerationExtension, CompatContext by compatContext {

  @OptIn(UnsafeDuringIrConstructionAPI::class)
  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    val diagnosticReporter = pluginContext.diagnosticReporter
    val generatedAnnotation = generatedAnnotationName?.let { name ->
      pluginContext.finderForBuiltinsCompat().findClass(name).also {
        if (it == null) {
          diagnosticReporter.error { "Unknown generated annotation $generatedAnnotationName" }
          return
        }
      }
    }
    val deferred = mutableListOf<GeneratedAdapter>()
    val moshiTransformer =
      MoshiIrVisitor(
        moduleFragment,
        pluginContext,
        generatedAnnotation,
        enableSealed,
        deferred,
        compatContext,
      )
    moduleFragment.transform(moshiTransformer, null)
    for ((file, adapters) in deferred.groupBy { it.irFile }) {
      for (adapter in adapters) {
        file.declarations.add(adapter.adapterClass)
        adapter.adapterClass.parent = file
      }
    }
  }
}
