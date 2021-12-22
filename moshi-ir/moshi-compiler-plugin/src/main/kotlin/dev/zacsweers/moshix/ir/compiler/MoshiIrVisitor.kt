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

import dev.zacsweers.moshix.ir.compiler.api.AdapterGenerator
import dev.zacsweers.moshix.ir.compiler.api.PropertyGenerator
import dev.zacsweers.moshix.ir.compiler.util.dumpSrc
import dev.zacsweers.moshix.ir.compiler.util.error
import dev.zacsweers.moshix.ir.compiler.util.locationOf
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageUtil
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.source.getPsi

internal const val LOG_PREFIX = "*** MOSHI (IR):"
private val JSON_CLASS_ANNOTATION = FqName("com.squareup.moshi.JsonClass")

internal data class GeneratedAdapter(val adapterClass: IrDeclaration, val irFile: IrFile)

@OptIn(ObsoleteDescriptorBasedAPI::class)
internal class MoshiIrVisitor(
    moduleFragment: IrModuleFragment,
    private val pluginContext: IrPluginContext,
    private val messageCollector: MessageCollector,
    private val deferredAddedClasses: MutableList<GeneratedAdapter>
) : IrElementTransformerVoidWithContext() {

  private val moshiSymbols = MoshiSymbols(pluginContext.irBuiltIns, moduleFragment, pluginContext)

  private fun adapterGenerator(
      originalType: IrClass,
  ): AdapterGenerator? {
    val type = targetType(originalType, pluginContext, messageCollector) ?: return null

    val properties = mutableMapOf<String, PropertyGenerator>()
    for (property in type.properties.values) {
      val generator = property.generator(messageCollector, originalType)
      if (generator != null) {
        properties[property.name] = generator
      }
    }

    for ((name, parameter) in type.constructor.parameters) {
      if (type.properties[parameter.name] == null && !parameter.hasDefault) {
        // TODO would be nice if we could pass the parameter node directly?
        messageCollector.error({ originalType.file.locationOf(originalType) }) {
          "No property for required constructor parameter $name"
        }
        return null
      }
    }

    // Sort properties so that those with constructor parameters come first.
    val sortedProperties =
        properties.values.sortedBy {
          if (it.hasConstructorParameter) {
            it.target.parameterIndex
          } else {
            Integer.MAX_VALUE
          }
        }

    return AdapterGenerator(pluginContext, moshiSymbols, type, sortedProperties)
  }

  override fun visitClassNew(declaration: IrClass): IrStatement {
    declaration.getAnnotation(JSON_CLASS_ANNOTATION)?.let { call ->
      call.getValueArgument(0)?.let { generateAdapter ->
        // This is generateAdapter
        @Suppress("UNCHECKED_CAST")
        if (!(generateAdapter as IrConst<Boolean>).value) {
          return super.visitClassNew(declaration)
        }

        if (call.valueArgumentsCount >= 2) {
          // This is generator
          call.getValueArgument(1)?.let { generator ->
            @Suppress("UNCHECKED_CAST")
            if ((generator as IrConst<String>).value.isNotBlank()) {
              return super.visitClassNew(declaration)
            }
          }
        }

        val adapterGenerator =
            adapterGenerator(declaration) ?: return super.visitClassNew(declaration)
        pluginContext.irFactory.run {
          val adapterClass = adapterGenerator.prepare()
          println("Dumping current IR src")
          println(adapterClass.adapterClass.dumpSrc())
          deferredAddedClasses += GeneratedAdapter(adapterClass.adapterClass, declaration.file)
        }
      }
    }
    return super.visitClassNew(declaration)
  }

  private fun IrClass.reportError(message: String) {
    val location = MessageUtil.psiElementToMessageLocation(descriptor.source.getPsi())
    messageCollector.report(CompilerMessageSeverity.ERROR, "$LOG_PREFIX $message", location)
  }
}
