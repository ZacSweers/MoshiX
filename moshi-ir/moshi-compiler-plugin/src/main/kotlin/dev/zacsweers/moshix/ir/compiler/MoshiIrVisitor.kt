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

import dev.zacsweers.moshix.ir.compiler.api.MoshiAdapterGenerator
import dev.zacsweers.moshix.ir.compiler.api.PropertyGenerator
import dev.zacsweers.moshix.ir.compiler.sealed.MoshiSealedSymbols
import dev.zacsweers.moshix.ir.compiler.sealed.SealedAdapterGenerator
import dev.zacsweers.moshix.ir.compiler.util.dumpSrc
import dev.zacsweers.moshix.ir.compiler.util.error
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.name.FqName

private val JSON_CLASS_ANNOTATION = FqName("com.squareup.moshi.JsonClass")

internal data class GeneratedAdapter(val adapterClass: IrDeclaration, val irFile: IrFile)

internal class MoshiIrVisitor(
  moduleFragment: IrModuleFragment,
  private val pluginContext: IrPluginContext,
  private val messageCollector: MessageCollector,
  private val generatedAnnotation: IrClassSymbol?,
  private val enableSealed: Boolean,
  private val deferredAddedClasses: MutableList<GeneratedAdapter>,
  private val debug: Boolean,
) : IrElementTransformerVoidWithContext() {

  private val moshiSymbols by lazy {
    MoshiSymbols(pluginContext.irBuiltIns, moduleFragment, pluginContext)
  }

  private val moshiSealedSymbols by lazy { MoshiSealedSymbols(moshiSymbols) }

  private fun adapterGenerator(
    originalType: IrClass,
  ): MoshiAdapterGenerator? {
    val type = targetType(originalType, pluginContext, messageCollector) ?: return null

    val properties = mutableMapOf<String, PropertyGenerator>()
    for (property in type.properties.values) {
      val errors = mutableListOf<(MessageCollector) -> Unit>()
      val generator = property.generator(originalType, errors)
      if (errors.isNotEmpty()) {
        for (error in errors) {
          error(messageCollector)
        }
        return null
      }
      if (generator != null) {
        properties[property.name] = generator
      }
    }

    for ((name, parameter) in type.constructor.parameters) {
      if (type.properties[parameter.name] == null && !parameter.hasDefault) {
        // TODO would be nice if we could pass the parameter node directly?
        messageCollector.error(originalType) {
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

    return MoshiAdapterGenerator(pluginContext, moshiSymbols, type, sortedProperties)
  }

  override fun visitClassNew(declaration: IrClass): IrStatement {
    declaration.getAnnotation(JSON_CLASS_ANNOTATION)?.let { call ->
      call.getValueArgument(0)?.let { generateAdapter ->
        // This is generateAdapter
        @Suppress("UNCHECKED_CAST")
        if (!(generateAdapter as IrConst<Boolean>).value) {
          return super.visitClassNew(declaration)
        }

        // This is generator
        @Suppress("UNCHECKED_CAST")
        val generatorValue = (call.getValueArgument(1) as? IrConst<String>?)?.value.orEmpty()
        val generator =
          if (generatorValue.isNotBlank()) {
            val labelKey = generatorValue.labelKey()
            if (enableSealed && labelKey != null) {
              SealedAdapterGenerator(
                pluginContext = pluginContext,
                logger = messageCollector,
                moshiSymbols = moshiSymbols,
                moshiSealedSymbols = moshiSealedSymbols,
                target = declaration,
                labelKey = labelKey
              )
            } else {
              return super.visitClassNew(declaration)
            }
          } else {
            // Unspecified/null - means it's empty/default.
            adapterGenerator(declaration)
          }

        val adapterGenerator = generator ?: return super.visitClassNew(declaration)
        try {
          val adapterClass = adapterGenerator.prepare() ?: return super.visitClassNew(declaration)
          if (generatedAnnotation != null) {
            // TODO add generated annotation
          }
          if (debug) {
            val irSrc = adapterClass.adapterClass.dumpSrc()
            messageCollector.report(
              CompilerMessageSeverity.STRONG_WARNING,
              "MOSHI: Dumping current IR src for ${adapterClass.adapterClass.name}\n$irSrc"
            )
          }
          deferredAddedClasses += GeneratedAdapter(adapterClass.adapterClass, declaration.file)
        } catch (e: Exception) {
          messageCollector.error(declaration) {
            "Error preparing adapter for ${declaration.fqNameWhenAvailable}"
          }
          throw e
        }
      }
    }
    return super.visitClassNew(declaration)
  }
}

internal fun IrConstructorCall.labelKey(checkGenerateAdapter: Boolean = true): String? {
  // This is generateAdapter
  @Suppress("UNCHECKED_CAST")
  if (checkGenerateAdapter && !(getValueArgument(0) as IrConst<Boolean>).value) {
    return null
  }

  // This is generator
  @Suppress("UNCHECKED_CAST")
  val generatorValue = (getValueArgument(1) as? IrConst<String>?)?.value.orEmpty()
  return generatorValue.labelKey()
}

internal fun String.labelKey(): String? {
  return if (isNotBlank()) {
    if (startsWith("sealed:")) {
      removePrefix("sealed:")
    } else {
      null
    }
  } else {
    // Unspecified/null - means it's empty/default.
    null
  }
}
