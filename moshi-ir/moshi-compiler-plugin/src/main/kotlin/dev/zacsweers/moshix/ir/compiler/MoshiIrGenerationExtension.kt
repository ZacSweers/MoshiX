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

import dev.zacsweers.moshix.ir.compiler.util.error
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.name.ClassId

internal class MoshiIrGenerationExtension(
  private val messageCollector: MessageCollector,
  private val generatedAnnotationName: ClassId?,
  private val enableSealed: Boolean,
  private val debug: Boolean
) : IrGenerationExtension {

  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    val generatedAnnotation =
      generatedAnnotationName?.let { name ->
        pluginContext.referenceClass(name).also {
          if (it == null) {
            messageCollector.error { "Unknown generated annotation $generatedAnnotationName" }
            return
          }
        }
      }
    val deferred = mutableListOf<GeneratedAdapter>()
    val moshiTransformer =
      MoshiIrVisitor(
        moduleFragment,
        pluginContext,
        messageCollector,
        generatedAnnotation,
        enableSealed,
        deferred,
        debug
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
