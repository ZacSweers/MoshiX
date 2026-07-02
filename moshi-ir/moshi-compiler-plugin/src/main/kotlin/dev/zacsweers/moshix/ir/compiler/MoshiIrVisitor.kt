// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.ir.compiler

import dev.zacsweers.metro.compiler.compat.CompatContext
import dev.zacsweers.moshix.ir.compiler.api.MoshiAdapterGenerator
import dev.zacsweers.moshix.ir.compiler.api.PropertyGenerator
import dev.zacsweers.moshix.ir.compiler.sealed.MoshiSealedSymbols
import dev.zacsweers.moshix.ir.compiler.sealed.SealedAdapterGenerator
import dev.zacsweers.moshix.ir.compiler.util.error
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
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
  private val generatedAnnotation: IrClassSymbol?,
  private val enableSealed: Boolean,
  private val deferredAddedClasses: MutableList<GeneratedAdapter>,
  private val compatContext: CompatContext,
) : IrElementTransformerVoidWithContext(), CompatContext by compatContext {

  private val moshiSymbols by lazy {
    MoshiSymbols(pluginContext.irBuiltIns, moduleFragment, pluginContext, compatContext)
  }

  private val moshiSealedSymbols by lazy { MoshiSealedSymbols(moshiSymbols) }

  private fun adapterGenerator(originalType: IrClass): MoshiAdapterGenerator {
    val type = targetType(originalType, pluginContext)

    val properties = mutableMapOf<String, PropertyGenerator>()
    for (property in type.properties.values) {
      property.generator()?.let { generator ->
        properties[property.name] = generator
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

    return MoshiAdapterGenerator(pluginContext, moshiSymbols, type, sortedProperties, compatContext)
  }

  override fun visitClassNew(declaration: IrClass): IrStatement {
    declaration.getAnnotation(JSON_CLASS_ANNOTATION)?.let { call ->
      call.constArgumentOfTypeAt<Boolean>(0)?.let { generateAdapter ->
        // This is generateAdapter
        @Suppress("UNCHECKED_CAST")
        if (!generateAdapter) {
          return super.visitClassNew(declaration)
        }

        // This is generator
        @Suppress("UNCHECKED_CAST")
        val generatorValue = call.constArgumentOfTypeAt<String>(1).orEmpty()
        val generator =
          if (generatorValue.isNotBlank()) {
            val labelKey = generatorValue.labelKey()
            if (enableSealed && labelKey != null) {
              SealedAdapterGenerator(
                pluginContext = pluginContext,
                moshiSymbols = moshiSymbols,
                moshiSealedSymbols = moshiSealedSymbols,
                target = declaration,
                labelKey = labelKey,
                compatContext = compatContext,
              )
            } else {
              return super.visitClassNew(declaration)
            }
          } else {
            // Unspecified/null - means it's empty/default.
            adapterGenerator(declaration)
          }

        try {
          val adapterClass = generator.prepare()
          if (generatedAnnotation != null) {
            // TODO add generated annotation
          }
          deferredAddedClasses += GeneratedAdapter(adapterClass.adapterClass, declaration.file)
        } catch (e: Exception) {
          pluginContext.diagnosticReporter.error(declaration) {
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
  if (checkGenerateAdapter && constArgumentOfTypeAt<Boolean>(0) != true) {
    return null
  }

  // This is generator
  @Suppress("UNCHECKED_CAST") val generatorValue = constArgumentOfTypeAt<String>(1).orEmpty()
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
