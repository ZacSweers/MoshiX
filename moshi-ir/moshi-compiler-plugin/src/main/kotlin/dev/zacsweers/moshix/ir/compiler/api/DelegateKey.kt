/*
 * Copyright (C) 2018 Zac Sweers
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
package dev.zacsweers.moshix.ir.compiler.api

import dev.zacsweers.moshix.ir.compiler.MoshiSymbols
import dev.zacsweers.moshix.ir.compiler.util.createIrBuilder
import dev.zacsweers.moshix.ir.compiler.util.irType
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classifierOrFail
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.isNullable
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import java.util.Locale

/** A JsonAdapter that can be used to encode and decode a particular field. */
public data class DelegateKey(
    private val type: IrType,
    private val jsonQualifiers: List<IrConstructorCall>,
) {
  public val nullable: Boolean
    get() = type.isNullable()

  /** Returns an adapter to use when encoding and decoding this property. */
  internal fun generateProperty(
    pluginContext: IrPluginContext,
    moshiSymbols: MoshiSymbols,
    adapterCls: IrClass,
    moshiParameter: IrValueParameter,
    typesParameter: IrValueParameter?,
    propertyName: String
  ): IrField {
    val qualifierNames =
        jsonQualifiers.joinToString("") {
          "At${it.type.rawType().fqNameWhenAvailable!!.shortName().asString()}"
        }

    val adapterName =
        "${type.toVariableName().replaceFirstChar { it.lowercase(Locale.US) }}${qualifierNames}Adapter"

    val typeClassifier = type.classifierOrFail
    val genericIndex = if (typeClassifier is IrTypeParameterSymbol) {
      typeClassifier.owner.index
    } else {
      -1
    }
    val adapterTypeName = moshiSymbols.jsonAdapter.typeWith(type)

    val field =
      adapterCls
        .addField {
          name = Name.identifier(adapterName)
          type = adapterTypeName
          visibility = DescriptorVisibilities.PRIVATE
          isFinal = true
        }
        .apply {
          initializer =
            pluginContext.createIrBuilder(symbol).run {
              irExprBody(
                irCall(
                  pluginContext.referenceClass(FqName("com.squareup.moshi.Moshi"))!!
                    .functions.single {
                      it.owner.name.identifier == "adapter" &&
                        it.owner.valueParameters.size == 3
                    })
                  .apply {
                    dispatchReceiver = irGet(moshiParameter)

                    // type
                    if (genericIndex == -1) {
                      // Use typeOf() intrinsic
                      putValueArgument(
                        0,
                        irCall(
                          pluginContext
                            .referenceProperties(
                              FqName("kotlin.reflect.javaType")
                            )
                            .first()
                            .owner
                            .getter!!)
                          .apply {
                            extensionReceiver =
                              irCall(
                                pluginContext
                                  .referenceFunctions(
                                    FqName("kotlin.reflect.typeOf")
                                  )
                                  .first())
                                .apply { putTypeArgument(0, type) }
                          })
                    } else {
                      // It's generic, get from types array
                      putValueArgument(
                        0,
                        irCall(moshiSymbols.arrayGet.symbol).apply {
                          dispatchReceiver = irGet(typesParameter!!)
                          putValueArgument(0, irInt(genericIndex))
                        })
                    }
                    // annotations
                    // TODO construct these
                    putValueArgument(
                      1,
                      irCall(
                        pluginContext
                          .referenceFunctions(
                            FqName("kotlin.collections.emptySet")
                          )
                          .first())
                        .apply { putTypeArgument(0, pluginContext.irType("kotlin.Annotation")) })
                    // field hint
                    putValueArgument(2, irString(propertyName))
                  })
            }
        }
    return field
  }
}

//private fun IrConstructorCall.asInstantiationExpression(): IrExpression {
//  // <Type>(args)
//  return CodeBlock.of("%T(%L)", typeName, members.joinToCode())
//}

/**
 * Returns a suggested variable name derived from a list of type names. This just concatenates,
 * yielding types like MapOfStringLong.
 */
private fun List<IrType>.toVariableNames() = joinToString("") { it.toVariableName() }

/** Returns a suggested variable name derived from a type name, like nullableListOfString. */
private fun IrType.toVariableName(): String {
  val base =
      when (val classifier = classifierOrNull) {
        is IrClassSymbol -> {
          val owner = classifier.owner
          val rawName = owner.fqNameWhenAvailable!!.shortName().asString()
          if (owner.typeParameters.isEmpty()) {
            rawName
          } else {
            rawName + "Of" + owner.typeParameters.map { it.defaultType }.toVariableNames()
          }
        }
        //    is WildcardTypeName -> (inTypes + outTypes).toVariableNames()
        is IrTypeParameterSymbol -> {
          classifier.owner.name.identifier // + classifier. .toVariableNames()
        }
        else -> throw IllegalArgumentException("Unrecognized type! $this")
      }

  return if (isNullable()) {
    "Nullable$base"
  } else {
    base
  }
}

/** Returns a suggested variable name derived from a type name, like nullableListOfString. */
private fun IrType.rawType(): IrClass {
  return when (val classifier = classifierOrNull) {
    is IrClassSymbol -> classifier.owner
    //    is WildcardTypeName -> (inTypes + outTypes).toVariableNames()
    else -> throw IllegalArgumentException("Unrecognized type! $this")
  }
}
