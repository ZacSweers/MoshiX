// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.ir.compiler.sealed

import dev.zacsweers.metro.compiler.compat.CompatContext
import dev.zacsweers.moshix.ir.compiler.MoshiSymbols
import dev.zacsweers.moshix.ir.compiler.api.AdapterGenerator
import dev.zacsweers.moshix.ir.compiler.api.PreparedAdapter
import dev.zacsweers.moshix.ir.compiler.constArgumentOfTypeAt
import dev.zacsweers.moshix.ir.compiler.labelKey
import dev.zacsweers.moshix.ir.compiler.util.addOverride
import dev.zacsweers.moshix.ir.compiler.util.createIrBuilder
import dev.zacsweers.moshix.ir.compiler.util.generateToStringFun
import dev.zacsweers.moshix.ir.compiler.util.irInvoke
import dev.zacsweers.moshix.ir.compiler.util.irType
import dev.zacsweers.moshix.ir.compiler.util.rawType
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irReturnUnit
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFactory
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.interpreter.hasAnnotation
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classifierOrFail
import org.jetbrains.kotlin.ir.types.createType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.addSimpleDelegatingConstructor
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.createThisReceiverParameter
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.getSimpleFunction
import org.jetbrains.kotlin.ir.util.isObject
import org.jetbrains.kotlin.ir.util.nonDispatchArguments
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.packageFqName
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal class SealedAdapterGenerator
private constructor(
  private val pluginContext: IrPluginContext,
  private val moshiSymbols: MoshiSymbols,
  private val moshiSealedSymbols: MoshiSealedSymbols,
  private val target: IrClass,
  private val labelKey: String,
  private val compatContext: CompatContext,
) : AdapterGenerator, CompatContext by compatContext {
  @OptIn(UnsafeDuringIrConstructionAPI::class)
  override fun prepare(): PreparedAdapter {
    var fallbackStrategy: FallbackStrategy? = null

    val useDefaultNull =
      target.hasAnnotation(FqName("dev.zacsweers.moshix.sealed.annotations.DefaultNull"))

    val fallbackAdapterAnnotation =
      target.getAnnotation(FqName("dev.zacsweers.moshix.sealed.annotations.FallbackJsonAdapter"))

    if (fallbackAdapterAnnotation != null) {
      val adapterType = fallbackAdapterAnnotation.arguments[0] as IrClassReference
      // TODO can we check adapter type is valid? Compiler will check it for us
      val adapterDeclaration = adapterType.classType.rawType()
      val constructor = adapterDeclaration.primaryConstructor!!
      val hasMoshiParam = constructor.nonDispatchParameters.isNotEmpty()
      fallbackStrategy =
        FallbackStrategy.FallbackAdapter(
          targetConstructor = constructor.symbol,
          hasMoshiParam = hasMoshiParam,
        )
    } else if (useDefaultNull) {
      fallbackStrategy = FallbackStrategy.Null
    }

    val objectSubtypes = mutableListOf<IrClass>()
    val sealedSubtypes =
      target.sealedSubclasses
        .map { it.owner }
        .flatMapTo(LinkedHashSet()) { subtype ->
          val isObject = subtype.kind == ClassKind.OBJECT
          if (
            isObject &&
              subtype.hasAnnotation(FqName("dev.zacsweers.moshix.sealed.annotations.DefaultObject"))
          ) {
            sequenceOf(Subtype.ObjectType(subtype))
          } else {
            walkTypeLabels(subtype, objectSubtypes)
          }
        }

    pluginContext.irFactory.run {
      return createType(
        targetType = target,
        labelKey = labelKey,
        fallbackStrategy = fallbackStrategy,
        generatedAnnotation = null,
        subtypes = sealedSubtypes,
        objectSubtypes = objectSubtypes,
      )
    }
  }

  @OptIn(UnsafeDuringIrConstructionAPI::class)
  private fun walkTypeLabels(
    subtype: IrClass,
    objectSubtypes: MutableList<IrClass>,
  ): Sequence<Subtype> {
    // If it's sealed, check if it's inheriting from our existing type or a separate/new branching
    // off point
    return if (subtype.modality == Modality.SEALED) {
      if (
        subtype.getAnnotation(FqName("dev.zacsweers.moshix.sealed.annotations.TypeLabel")) != null
      ) {
        // It's a different type, allow it to be used as a label and branch off from here.
        sequenceOf(addLabelKeyForType(subtype, objectSubtypes))
      } else {
        // Recurse, inheriting the top type
        subtype.sealedSubclasses.asSequence().flatMap {
          walkTypeLabels(
            subtype = it.owner,
            objectSubtypes = objectSubtypes,
          )
        }
      }
    } else {
      sequenceOf(addLabelKeyForType(subtype = subtype, objectSubtypes = objectSubtypes))
    }
  }

  @OptIn(UnsafeDuringIrConstructionAPI::class)
  private fun addLabelKeyForType(
    subtype: IrClass,
    objectSubtypes: MutableList<IrClass>,
  ): Subtype {
    // Regular subtype, read its label
    val labelAnnotation =
      subtype.getAnnotation(FqName("dev.zacsweers.moshix.sealed.annotations.TypeLabel"))!!

    @Suppress("UNCHECKED_CAST")
    val labels = mutableListOf(labelAnnotation.constArgumentOfTypeAt<String>(0)!!)

    @Suppress("UNCHECKED_CAST")
    val alternates =
      (labelAnnotation.nonDispatchArguments[1] as IrVararg?)?.elements.orEmpty().map {
        (it as IrConst).value as String
      }

    labels += alternates

    if (subtype.isObject) {
      objectSubtypes += subtype
    }

    return Subtype.ClassType(subtype, labels)
  }

  @OptIn(UnsafeDuringIrConstructionAPI::class)
  private fun IrFactory.createType(
    targetType: IrClass,
    labelKey: String,
    fallbackStrategy: FallbackStrategy?,
    generatedAnnotation: IrConstructorCall?,
    subtypes: Set<Subtype>,
    objectSubtypes: List<IrClass>,
  ): PreparedAdapter {
    val packageName = targetType.packageFqName!!.asString()
    val simpleNames =
      targetType.fqNameWhenAvailable!!
        .asString()
        .removePrefix(packageName)
        .removePrefix(".")
        .split(".")
    val adapterName = "${simpleNames.joinToString(separator = "_")}JsonAdapter"

    val adapterCls = buildClass {
      name = Name.identifier(adapterName)
      modality = Modality.FINAL
      kind = ClassKind.CLASS
      visibility = targetType.visibility // always public or internal
      origin = MoshiSealedOrigin
    }
      .apply {
        createThisReceiverParameter()
        val jsonAdapterType =
          pluginContext
            .irType(ClassId.fromString("com/squareup/moshi/JsonAdapter"))
            .classifierOrFail
            .typeWith(targetType.defaultType)
        superTypes = listOf(jsonAdapterType)
        val hasObjectSubtypes = objectSubtypes.isNotEmpty()
        val ctor =
          addSimpleDelegatingConstructor(
              moshiSymbols.jsonAdapter.constructors.single().owner,
              pluginContext.irBuiltIns,
              isPrimary = true,
            )
            .apply {
              addValueParameter {
                name = Name.identifier("moshi")
                type = moshiSymbols.moshi.defaultType
              }
            }

        val runtimeAdapter = addField {
          name = Name.identifier("runtimeAdapter")
          type = jsonAdapterType
          visibility = DescriptorVisibilities.PRIVATE
          isFinal = true
        }
          .apply {
            initializer =
              pluginContext.createIrBuilder(symbol).run {
                val ofCreatorExpression =
                  irCall(moshiSealedSymbols.pjafOf).apply {
                    typeArguments[0] = targetType.defaultType
                    arguments[0] = moshiSymbols.javaClassReference(this@run, targetType.defaultType)
                    arguments[1] = irString(labelKey)
                  }

                val moshiParam = ctor.nonDispatchParameters[0]
                val moshiAccess: IrExpression =
                  if (hasObjectSubtypes) {
                    val initial =
                      irCall(moshiSymbols.moshiNewBuilder).apply {
                        arguments[0] = irGet(moshiParam)
                      }
                    val newBuilds =
                      objectSubtypes.fold(initial) { receiver, subtype ->
                        irCall(moshiSymbols.addAdapter).apply {
                          typeArguments[0] = subtype.defaultType
                          arguments[0] = receiver
                          arguments[1] =
                            irCall(moshiSealedSymbols.objectJsonAdapterCtor).apply {
                              arguments[0] = irGetObject(subtype.symbol)
                            }
                        }
                      }
                    irCall(moshiSymbols.moshiBuilderBuild).apply { arguments[0] = newBuilds }
                  } else {
                    irGet(moshiParam)
                  }

                val subtypesExpression =
                  subtypes.filterIsInstance<Subtype.ClassType>().fold(ofCreatorExpression) {
                    receiver,
                    subtype ->
                    subtype.labels.fold(receiver) { nestedReceiver, label ->
                      irCall(moshiSealedSymbols.pjafWithSubtype).apply {
                        arguments[0] = nestedReceiver
                        arguments[1] =
                          moshiSymbols.javaClassReference(
                            this@run,
                            subtype.className.defaultType,
                          )
                        arguments[2] = irString(label)
                      }
                    }
                  }

                var finalFallbackStrategy = fallbackStrategy
                subtypes.filterIsInstance<Subtype.ObjectType>().firstOrNull()?.let { defaultObject
                  ->
                  if (fallbackStrategy == null) {
                    finalFallbackStrategy =
                      FallbackStrategy.DefaultObject(defaultObject.className.symbol)
                  }
                }

                val possiblyWithDefaultExpression =
                  finalFallbackStrategy?.statement(
                    builder = this,
                    moshiSealedSymbols = moshiSealedSymbols,
                    subtypesExpression = subtypesExpression,
                    targetType = targetType.defaultType,
                    moshiParam = moshiParam,
                  ) ?: subtypesExpression

                // .create(Message::class.java, emptySet(), moshi) as JsonAdapter<Message>
                irExprBody(
                  irAs(
                    irCall(moshiSymbols.jsonAdapterFactoryCreate).apply {
                      arguments[0] = possiblyWithDefaultExpression
                      arguments[1] =
                        moshiSymbols.javaClassReference(this@run, targetType.defaultType)

                      arguments[2] = irCall(moshiSymbols.emptySet)
                      arguments[3] = moshiAccess
                    },
                    jsonAdapterType,
                  )
                )
              }
          }

        generateFromJsonFun(runtimeAdapter)
        generateToJsonFun(runtimeAdapter)
        generateToStringFun(
          pluginContext,
          simpleNames.joinToString("."),
          generatedName = "GeneratedSealedJsonAdapter",
        )
      }

    return PreparedAdapter(adapterCls)
  }

  @OptIn(UnsafeDuringIrConstructionAPI::class)
  private fun IrClass.generateToJsonFun(delegateField: IrField): IrFunction {
    return addOverride(
        FqName("com.squareup.moshi.JsonAdapter"),
        Name.identifier("toJson").identifier,
        pluginContext.irBuiltIns.unitType,
        modality = Modality.OPEN,
      ) { function ->
        function.nonDispatchParameters.size == 2 &&
          function.nonDispatchParameters[0].type.classifierOrFail == moshiSymbols.jsonWriter
      }
      .apply {
        val writer = addValueParameter {
          name = Name.identifier("writer")
          type =
            moshiSymbols.jsonWriter.createType(hasQuestionMark = false, arguments = emptyList())
        }
        val value = addValueParameter {
          name = Name.identifier("value")
          type = pluginContext.irBuiltIns.anyNType
        }
        body =
          DeclarationIrBuilder(pluginContext, symbol).irBlockBody {
            +irCall(moshiSymbols.jsonAdapter.getSimpleFunction("toJson")!!).apply {
              arguments[0] = irGetField(irGet(dispatchReceiverParameter!!), delegateField)
              arguments[1] = irGet(writer)
              arguments[2] = irGet(value)
            }
            +irReturnUnit()
          }
      }
  }

  @OptIn(UnsafeDuringIrConstructionAPI::class)
  private fun IrClass.generateFromJsonFun(delegateField: IrField): IrFunction {
    return addOverride(
        FqName("com.squareup.moshi.JsonAdapter"),
        Name.identifier("fromJson").identifier,
        pluginContext.irBuiltIns.anyNType,
        modality = Modality.OPEN,
      ) { function ->
        function.nonDispatchParameters.size == 1 &&
          function.nonDispatchParameters[0].type.classifierOrFail == moshiSymbols.jsonReader
      }
      .apply {
        val readerParam = addValueParameter {
          name = Name.identifier("reader")
          type = moshiSymbols.jsonReader.defaultType
        }
        body =
          DeclarationIrBuilder(pluginContext, symbol).irBlockBody {
            +irReturn(
              irCall(moshiSymbols.jsonAdapter.getSimpleFunction("fromJson")!!).apply {
                arguments[0] = irGetField(irGet(dispatchReceiverParameter!!), delegateField)
                arguments[1] = irGet(readerParam)
              }
            )
          }
      }
  }

  companion object {
    operator fun invoke(
      pluginContext: IrPluginContext,
      moshiSymbols: MoshiSymbols,
      moshiSealedSymbols: MoshiSealedSymbols,
      target: IrClass,
      labelKey: String,
      compatContext: CompatContext,
    ): AdapterGenerator {
      return SealedAdapterGenerator(
        pluginContext = pluginContext,
        moshiSymbols = moshiSymbols,
        moshiSealedSymbols = moshiSealedSymbols,
        target = target,
        labelKey = labelKey,
        compatContext = compatContext,
      )
    }
  }
}

private sealed class Subtype(val className: IrClass) {
  class ObjectType(className: IrClass) : Subtype(className)

  class ClassType(className: IrClass, val labels: List<String>) : Subtype(className)
}

private sealed interface FallbackStrategy {
  fun statement(
    builder: IrBuilderWithScope,
    moshiSealedSymbols: MoshiSealedSymbols,
    subtypesExpression: IrExpression,
    targetType: IrType,
    moshiParam: IrValueParameter,
  ): IrCall

  object Null : FallbackStrategy {
    override fun statement(
      builder: IrBuilderWithScope,
      moshiSealedSymbols: MoshiSealedSymbols,
      subtypesExpression: IrExpression,
      targetType: IrType,
      moshiParam: IrValueParameter,
    ) =
      with(builder) {
        irCall(moshiSealedSymbols.pjafWithDefaultValue).apply {
          arguments[0] = subtypesExpression
          arguments[1] = irNull(targetType)
        }
      }
  }

  class FallbackAdapter(val targetConstructor: IrFunctionSymbol, val hasMoshiParam: Boolean) :
    FallbackStrategy {
    override fun statement(
      builder: IrBuilderWithScope,
      moshiSealedSymbols: MoshiSealedSymbols,
      subtypesExpression: IrExpression,
      targetType: IrType,
      moshiParam: IrValueParameter,
    ): IrCall {
      return with(builder) {
        irCall(moshiSealedSymbols.pjafWithFallbackJsonAdapter).apply {
          arguments[0] = subtypesExpression
          // TODO cast to JsonAdapter<Any>
          val args =
            if (hasMoshiParam) {
              arrayOf(irGet(moshiParam))
            } else {
              emptyArray()
            }
          arguments[1] = irInvoke(callee = targetConstructor, args = args)
        }
      }
    }
  }

  class DefaultObject(val defaultObject: IrClassSymbol) : FallbackStrategy {
    override fun statement(
      builder: IrBuilderWithScope,
      moshiSealedSymbols: MoshiSealedSymbols,
      subtypesExpression: IrExpression,
      targetType: IrType,
      moshiParam: IrValueParameter,
    ): IrCall {
      return with(builder) {
        irCall(moshiSealedSymbols.pjafWithDefaultValue).apply {
          arguments[0] = subtypesExpression
          arguments[1] = irGetObject(defaultObject)
        }
      }
    }
  }
}
