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
import dev.zacsweers.moshix.ir.compiler.util.rawType
import java.util.Locale
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irImplicitCast
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.classifierOrFail
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.isNullable
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.Variance

/** A JsonAdapter that can be used to encode and decode a particular field. */
internal data class DelegateKey(
  private val delegateType: IrType,
  private val jsonQualifiers: List<IrConstructorCall>,
) {
  val nullable: Boolean
    get() = delegateType.isNullable()

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
      "${delegateType.toVariableName().replaceFirstChar { it.lowercase(Locale.US) }}${qualifierNames}Adapter"

    val typeClassifier = delegateType.classifierOrFail
    val genericIndex =
      if (typeClassifier is IrTypeParameterSymbol) {
        typeClassifier.owner.index
      } else {
        -1
      }
    val typedAdapter = moshiSymbols.jsonAdapter.typeWith(delegateType)

    val field =
      adapterCls
        .addField {
          name = Name.identifier(adapterName)
          type = typedAdapter
          visibility = DescriptorVisibilities.PRIVATE
          isFinal = true
        }
        .apply {
          initializer =
            pluginContext
              .createIrBuilder(symbol)
              .moshiAdapterCall(
                pluginContext,
                moshiSymbols,
                delegateType,
                moshiParameter,
                typesParameter,
                genericIndex,
                propertyName,
                jsonQualifiers
              )
        }
    return field
  }
}

private fun IrBuilderWithScope.moshiAdapterCall(
  pluginContext: IrPluginContext,
  moshiSymbols: MoshiSymbols,
  delegateType: IrType,
  moshiParameter: IrValueParameter,
  typesParameter: IrValueParameter?,
  genericIndex: Int,
  propertyName: String,
  jsonQualifiers: List<IrConstructorCall>
): IrExpressionBody {
  return irExprBody(
    irCall(moshiSymbols.moshiThreeArgAdapter).apply {
      dispatchReceiver = irGet(moshiParameter)

      addTypeParam(this, moshiSymbols, delegateType, genericIndex, typesParameter)
      addAnnotationsParam(this, pluginContext, moshiSymbols, jsonQualifiers)
      putValueArgument(2, irString(propertyName))
    }
  )
}

private fun IrBuilderWithScope.addTypeParam(
  irCall: IrCall,
  moshiSymbols: MoshiSymbols,
  delegateType: IrType,
  genericIndex: Int,
  typesParameter: IrValueParameter?,
) =
  irCall.apply {
    // type
    if (genericIndex == -1) {
      // Use typeOf() intrinsic
      putValueArgument(0, renderType(moshiSymbols, delegateType, typesParameter))
    } else {
      // It's generic, get from types array
      putValueArgument(
        0,
        irCall(moshiSymbols.arrayGet.symbol).apply {
          dispatchReceiver = irGet(typesParameter!!)
          putValueArgument(0, irInt(genericIndex))
        }
      )
    }
  }

private fun IrBuilderWithScope.addAnnotationsParam(
  irCall: IrCall,
  pluginContext: IrPluginContext,
  moshiSymbols: MoshiSymbols,
  jsonQualifiers: List<IrConstructorCall>
) =
  irCall.apply {
    val argumentExpression =
      if (jsonQualifiers.isEmpty()) {
        irCall(moshiSymbols.emptySet).apply {
          putTypeArgument(0, pluginContext.irType(ClassId.fromString("kotlin/Annotation")))
        }
      } else {
        val callee: IrFunctionSymbol
        val argExpression: IrExpression
        if (jsonQualifiers.size == 1) {
          callee = moshiSymbols.setOfSingleton
          argExpression = jsonQualifiers[0]
        } else {
          callee = moshiSymbols.setOfVararg
          argExpression = irVararg(pluginContext.irBuiltIns.annotationType, jsonQualifiers)
        }
        irCall(
            callee = callee,
            type =
              pluginContext.irBuiltIns.setClass.typeWith(pluginContext.irBuiltIns.annotationType),
            typeArguments = listOf(pluginContext.irBuiltIns.annotationType)
          )
          .apply { putValueArgument(0, argExpression) }
      }
    putValueArgument(1, argumentExpression)
  }

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
      is IrTypeParameterSymbol -> {
        classifier.owner.name.identifier
      }
      else -> throw IllegalArgumentException("Unrecognized type! $this")
    }

  return if (isNullable()) {
    "Nullable$base"
  } else {
    base
  }
}

/**
 * Returns an [IrExpression] that is passed to the adapter(Type) parameter. We don't use the
 * typeOf() intrinsics from the stdlib because it does unnecessary reflection at runtime since we
 * are too late to properly use it hear, and also avoids some possible conflicts with
 * kotlin-reflect.
 */
private fun IrBuilderWithScope.renderType(
  moshiSymbols: MoshiSymbols,
  delegateType: IrType,
  typesParameter: IrValueParameter?,
  forceBoxing: Boolean = false
): IrExpression {
  check(delegateType is IrSimpleType) { "Expected a SimpleType, got $delegateType" }
  val classifier = delegateType.classifier

  if (classifier is IrTypeParameterSymbol) {
    // Get the type from the types array param
    val index = classifier.owner.index
    return irCall(moshiSymbols.arrayGet.symbol).apply {
      dispatchReceiver = irGet(typesParameter!!)
      putValueArgument(0, irInt(index))
    }
  }

  check(classifier is IrClassSymbol) { "Expected Class, got $classifier" }
  val irClass = classifier.owner

  // If it's an Array type, we shortcut this to return Types.arrayOf()
  // Special-case primitive arrays
  val builtIns = moshiSymbols.pluginContext.irBuiltIns
  builtIns.primitiveArraysToPrimitiveTypes[irClass.symbol]?.let { primitiveArrayType ->
    return irCall(moshiSymbols.moshiTypesArrayOf).apply {
      putValueArgument(
        0,
        renderType(
          moshiSymbols,
          builtIns.primitiveTypeToIrType.getValue(primitiveArrayType),
          typesParameter
        )
      )
    }
  }
  if (irClass.symbol == builtIns.arrayClass) {
    return irCall(moshiSymbols.moshiTypesArrayOf).apply {
      putValueArgument(
        0,
        renderType(
          moshiSymbols,
          delegateType.arguments[0].typeOrNull!!,
          typesParameter,
          forceBoxing = true
        )
      )
    }
  }

  // Standard generic type
  return if (delegateType.arguments.isEmpty()) {
    irImplicitCast(
      moshiSymbols.javaClassReference(this, delegateType, forceObjectType = forceBoxing),
      moshiSymbols.type.defaultType
    )
  } else {
    // Build the generic
    val arguments = mutableListOf<IrExpression>()
    val parentClass = irClass.parentClassOrNull
    val symbol =
      if (parentClass != null) {
        arguments +=
          renderType(moshiSymbols, parentClass.defaultType, typesParameter, forceBoxing = true)
        moshiSymbols.moshiTypesNewParameterizedTypeWithOwner
      } else {
        moshiSymbols.moshiTypesNewParameterizedType
      }

    arguments += moshiSymbols.javaClassReference(this, delegateType, forceObjectType = true)

    irCall(symbol).apply {
      for ((i, arg) in arguments.withIndex()) {
        putValueArgument(i, arg)
      }
      putValueArgument(
        arguments.size,
        irVararg(
          moshiSymbols.type.defaultType,
          delegateType.arguments.map { typeArg ->
            if (typeArg is IrTypeProjection && typeArg.variance != Variance.INVARIANT) {
              // Wildcard type
              val renderedType =
                renderType(moshiSymbols, typeArg.type, typesParameter, forceBoxing = true)
              val targetMethod =
                when (typeArg.variance) {
                  Variance.IN_VARIANCE -> moshiSymbols.moshiTypesSuperTypeOf
                  Variance.OUT_VARIANCE -> moshiSymbols.moshiTypesSubtypeOf
                  Variance.INVARIANT -> error("Not possible")
                }
              return@map irCall(targetMethod).apply { putValueArgument(0, renderedType) }
            }
            val type = typeArg.typeOrNull
            if (type != null) {
              renderType(moshiSymbols, type, typesParameter, forceBoxing = true)
            } else {
              // Star projection
              irCall(moshiSymbols.moshiTypesSubtypeOf).apply {
                putValueArgument(
                  0,
                  renderType(moshiSymbols, builtIns.anyType, typesParameter, forceBoxing = true)
                )
              }
            }
          }
        )
      )
    }
  }
}
