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
package dev.zacsweers.moshix.ir.compiler.api

import dev.zacsweers.moshix.ir.compiler.MoshiSymbols
import dev.zacsweers.moshix.ir.compiler.api.FromJsonComponent.ParameterOnly
import dev.zacsweers.moshix.ir.compiler.api.FromJsonComponent.ParameterProperty
import dev.zacsweers.moshix.ir.compiler.api.FromJsonComponent.PropertyOnly
import dev.zacsweers.moshix.ir.compiler.util.NameAllocator
import dev.zacsweers.moshix.ir.compiler.util.addOverride
import dev.zacsweers.moshix.ir.compiler.util.createIrBuilder
import dev.zacsweers.moshix.ir.compiler.util.defaultPrimitiveValue
import dev.zacsweers.moshix.ir.compiler.util.irBinOp
import dev.zacsweers.moshix.ir.compiler.util.irConstructorBody
import dev.zacsweers.moshix.ir.compiler.util.irInstanceInitializerCall
import dev.zacsweers.moshix.ir.compiler.util.irType
import dev.zacsweers.moshix.ir.compiler.util.rawType
import dev.zacsweers.moshix.ir.compiler.util.rawTypeOrNull
import org.jetbrains.kotlin.backend.common.deepCopyWithVariables
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irIfThen
import org.jetbrains.kotlin.backend.common.lower.irNot
import org.jetbrains.kotlin.backend.common.lower.irThrow
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.declarations.buildValueParameter
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.builders.irBranch
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irConcat
import org.jetbrains.kotlin.ir.builders.irElseBranch
import org.jetbrains.kotlin.ir.builders.irEquals
import org.jetbrains.kotlin.ir.builders.irEqualsNull
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irImplicitCast
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irNotEquals
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irReturnUnit
import org.jetbrains.kotlin.ir.builders.irSet
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.builders.irWhen
import org.jetbrains.kotlin.ir.builders.irWhile
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFactory
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrDelegatingConstructorCall
import org.jetbrains.kotlin.ir.expressions.impl.IrDelegatingConstructorCallImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeArgument
import org.jetbrains.kotlin.ir.types.classifierOrFail
import org.jetbrains.kotlin.ir.types.createType
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.ir.util.getSimpleFunction
import org.jetbrains.kotlin.ir.util.packageFqName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.util.OperatorNameConventions

internal class AdapterGenerator(
    private val pluginContext: IrPluginContext,
    private val moshiSymbols: MoshiSymbols,
    private val target: TargetType,
    private val propertyList: List<PropertyGenerator>,
) {
  private val nonTransientProperties = propertyList.filterNot { it.isTransient }
  private val className = target.typeName.rawType()
  private val visibility = target.visibility
  private val typeVariables = target.typeVariables
  private val targetConstructorParams =
      target.constructor.parameters.mapKeys { (_, param) -> param.index }

  private val nameAllocator = NameAllocator()
  private val packageName = className.packageFqName!!.asString()
  private val simpleNames =
      className.fqNameWhenAvailable!!
          .asString()
          .removePrefix(packageName)
          .removePrefix(".")
          .split(".")
  private val adapterName = "${simpleNames.joinToString(separator = "_")}JsonAdapter"

  private fun irType(
      qualifiedName: String,
      nullable: Boolean = false,
      arguments: List<IrTypeArgument> = emptyList()
  ) = pluginContext.irType(qualifiedName, nullable, arguments)

  fun prepare(): PreparedAdapter {
    val reservedSimpleNames = mutableSetOf<String>()
    for (property in nonTransientProperties) {
      // Allocate names for simple property types first to avoid collisions
      // See https://github.com/square/moshi/issues/1277
      property.target.type.rawTypeOrNull()?.name?.identifier?.let { simpleNameToReserve ->
        if (reservedSimpleNames.add(simpleNameToReserve)) {
          nameAllocator.newName(simpleNameToReserve)
        }
      }
      property.allocateNames(nameAllocator)
    }

    val generatedAdapter = pluginContext.irFactory.generateType()
    return PreparedAdapter(generatedAdapter)
  }

  private fun IrFactory.generateType(): IrClass {
    val adapterCls = buildClass {
      name = Name.identifier(adapterName)
      modality = Modality.FINAL
      kind = ClassKind.CLASS
      visibility = this@AdapterGenerator.visibility // always public or internal
    }

    adapterCls.origin = MoshiOrigin
    val isGeneric = typeVariables.isNotEmpty()
    adapterCls.typeParameters = typeVariables

    val adapterReceiver =
        buildValueParameter(adapterCls) {
          name = Name.special("<this>")
          type =
              IrSimpleTypeImpl(
                  classifier = adapterCls.symbol,
                  hasQuestionMark = false,
                  arguments = emptyList(),
                  annotations = emptyList())
          origin = IrDeclarationOrigin.INSTANCE_RECEIVER
        }
    adapterCls.thisReceiver = adapterReceiver
    adapterCls.superTypes =
        listOf(irType("com.squareup.moshi.JsonAdapter").classifierOrFail.typeWith(target.typeName))

    val ctor = adapterCls.generateConstructor(isGeneric)
    val optionsField = adapterCls.generateOptionsField()

    val adapterProperties = mutableMapOf<DelegateKey, IrField>()
    for (uniqueAdapter in nonTransientProperties.distinctBy { it.delegateKey }) {
      adapterProperties[uniqueAdapter.delegateKey] =
          uniqueAdapter.delegateKey.generateProperty(
              pluginContext,
              moshiSymbols,
              adapterCls,
              ctor.valueParameters[0],
              ctor.valueParameters.getOrNull(1),
              uniqueAdapter.name)
    }

    adapterCls.generateToStringFun()
    adapterCls.generateToJsonFun(adapterProperties)
    adapterCls.generateFromJsonFun(adapterProperties, optionsField)

    return adapterCls
  }

  private fun IrClass.generateConstructor(isGeneric: Boolean): IrConstructor {
    val adapterCls = this
    val ctor =
        adapterCls
            .addConstructor {
              isPrimary = true
              returnType = adapterCls.defaultType
            }
            .apply {
              addValueParameter {
                name = Name.identifier("moshi")
                type = irType("com.squareup.moshi.Moshi")
              }
              if (isGeneric) {
                addValueParameter {
                  name = Name.identifier("types")
                  type =
                      pluginContext.irBuiltIns.arrayClass.typeWith(irType("java.lang.reflect.Type"))
                }
              }
            }
    ctor.irConstructorBody(pluginContext) { statements ->
      statements += generateJsonAdapterSuperConstructorCall()
      statements +=
          irInstanceInitializerCall(
              context = pluginContext,
              classSymbol = adapterCls.symbol,
          )

      // Size check for types array. Must be equal to the number of type parameters
      // require(types.size == 1) {
      //   "TypeVariable mismatch: Expecting 1 type(s) for generic type variables [T], but received
      // ${types.size} with values $types"
      // }
      if (isGeneric) {
        val expectedSize = typeVariables.size
        statements +=
            DeclarationIrBuilder(pluginContext, ctor.symbol).irBlock {
              val receivedSize =
                  irTemporary(
                      irCall(moshiSymbols.arraySizeGetter).apply {
                        dispatchReceiver = irGet(ctor.valueParameters[1])
                      },
                      nameHint = "receivedSize")
              +irIfThen(
                  condition = irNotEquals(irGet(receivedSize), irInt(expectedSize)),
                  thenPart =
                      irThrow(
                          irCall(pluginContext.irBuiltIns.illegalArgumentExceptionSymbol).apply {
                            putValueArgument(
                                0,
                                irConcat().apply {
                                  addArgument(irString("TypeVariable mismatch: Expecting "))
                                  addArgument(irInt(expectedSize))
                                  val typeWord = if (expectedSize == 1) "type" else "types"
                                  addArgument(irString(" $typeWord for generic type variables ["))
                                  addArgument(
                                      irString(
                                          typeVariables.joinToString(separator = ", ") {
                                            it.name.asString()
                                          }))
                                  addArgument(irString("], but received "))
                                  addArgument(irGet(receivedSize))
                                })
                          }))
            }
      }
    }

    return ctor
  }

  private fun IrClass.generateOptionsField(): IrField {
    return addField {
      name = Name.identifier("options")
      type = irType("com.squareup.moshi.JsonReader.Options")
      visibility = DescriptorVisibilities.PRIVATE
      isFinal = true
    }
        .apply {
          initializer =
              pluginContext.createIrBuilder(symbol).run {
                // TODO put this in MoshiSymbols
                val functionRef =
                    pluginContext.referenceClass(FqName("com.squareup.moshi.JsonReader.Options"))!!
                        .functions.single { it.owner.name.asString() == "of" }
                irExprBody(
                    irCall(functionRef).apply {
                      val args = nonTransientProperties.map { irString(it.jsonName) }
                      this.putValueArgument(0, irVararg(pluginContext.irBuiltIns.stringType, args))
                    })
              }
        }
  }

  private fun IrClass.generateToStringFun(): IrFunction {
    return addOverride(
        FqName("kotlin.Any"),
        Name.identifier("toString").identifier,
        pluginContext.irBuiltIns.stringType,
        modality = Modality.OPEN,
    )
        .apply {
          body =
              DeclarationIrBuilder(pluginContext, symbol).irBlockBody {
                +irReturn(
                    irConcat().apply {
                      addArgument(irString("GeneratedJsonAdapter("))
                      addArgument(irString(simpleNames.joinToString(".")))
                      addArgument(irString(")"))
                    })
              }
        }
  }

  private fun IrClass.generateToJsonFun(adapterProperties: Map<DelegateKey, IrField>): IrFunction {
    return addOverride(
        FqName("com.squareup.moshi.JsonAdapter"),
        Name.identifier("toJson").identifier,
        pluginContext.irBuiltIns.unitType,
        modality = Modality.OPEN) { function ->
      function.valueParameters.size == 2 &&
          function.valueParameters[0].type.classifierOrFail == moshiSymbols.jsonWriter
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
                +irIfThen(
                    irEqualsNull(irGet(value)),
                    irThrow(
                        irCall(
                            // TODO why can't I use kotlin.NullPointerException here?
                            pluginContext.referenceClass(
                                    FqName("kotlin.KotlinNullPointerException"))!!
                                .constructors.first { it.owner.valueParameters.size == 1 })
                            .apply {
                              putValueArgument(
                                  0,
                                  irString(
                                      "value was null! Wrap in .nullSafe() to write nullable values."))
                            }))
                // Cast it up to the actual type
                val castValue =
                    irTemporary(
                        irImplicitCast(irGet(value), target.typeName.makeNotNull()), "castValue")
                +irCall(moshiSymbols.jsonWriter.getSimpleFunction("beginObject")!!).apply {
                  dispatchReceiver = irGet(writer)
                }
                for (property in nonTransientProperties) {
                  +irCall(moshiSymbols.jsonWriter.getSimpleFunction("name")!!).apply {
                    dispatchReceiver = irGet(writer)
                    putValueArgument(0, irString(property.jsonName))
                  }
                  // adapter.toJson(writer, value.prop)
                  +irCall(moshiSymbols.jsonAdapter.getSimpleFunction("toJson")!!).apply {
                    // adapter.
                    dispatchReceiver =
                        irGetField(
                            irGet(dispatchReceiverParameter!!),
                            adapterProperties.getValue(property.delegateKey))
                    // writer
                    putValueArgument(0, irGet(writer))
                    // value.prop
                    putValueArgument(
                        1,
                        irCall(target.irClass.getPropertyGetter(property.name)!!).apply {
                          dispatchReceiver = irGet(castValue)
                        })
                  }
                }
                +irCall(moshiSymbols.jsonWriter.getSimpleFunction("endObject")!!).apply {
                  dispatchReceiver = irGet(writer)
                }
                +irReturnUnit()
              }
        }
  }

  private fun IrClass.generateFromJsonFun(
      adapterProperties: Map<DelegateKey, IrField>,
      optionsField: IrField
  ): IrFunction {
    return addOverride(
        FqName("com.squareup.moshi.JsonAdapter"),
        Name.identifier("fromJson").identifier,
        pluginContext.irBuiltIns.anyNType,
        modality = Modality.OPEN,
    ) { function ->
      function.valueParameters.size == 1 &&
          function.valueParameters[0].type.classifierOrFail == moshiSymbols.jsonReader
    }
        .apply {
          val readerParam = addValueParameter {
            name = Name.identifier("reader")
            type = irType("com.squareup.moshi.JsonReader")
          }
          body =
              DeclarationIrBuilder(pluginContext, symbol).irBlockBody {
                val localVars = mutableMapOf<String, IrVariable>()
                for (property in nonTransientProperties) {
                  localVars[property.localName] =
                      property.generateLocalProperty(this, pluginContext)
                  if (property.hasLocalIsPresentName) {
                    localVars[property.localIsPresentName] =
                        property.generateLocalIsPresentProperty(this, pluginContext)
                  }
                }

                val propertiesByIndex =
                    propertyList.asSequence().filter { it.hasConstructorParameter }.associateBy {
                      it.target.parameterIndex
                    }
                val components = mutableListOf<FromJsonComponent>()

                // Add parameters (± properties) first, their index matters
                for ((index, parameter) in targetConstructorParams) {
                  val property = propertiesByIndex[index]
                  if (property == null) {
                    components += ParameterOnly(parameter)
                  } else {
                    components += ParameterProperty(parameter, property)
                  }
                }

                // Now add the remaining properties that aren't parameters
                for (property in propertyList) {
                  if (property.target.parameterIndex in targetConstructorParams) {
                    continue // Already handled
                  }
                  if (property.isTransient) {
                    continue // We don't care about these outside of constructor parameters
                  }
                  components += PropertyOnly(property)
                }

                // Calculate how many masks we'll need. Round up if it's not evenly divisible by 32
                val propertyCount = targetConstructorParams.size
                val maskCount =
                    if (propertyCount == 0) {
                      0
                    } else {
                      (propertyCount + 31) / 32
                    }
                // Allocate mask names
                val bitMasks =
                    (0 until maskCount).map {
                      irTemporary(irInt(-1), "bitMask$it", isMutable = true)
                    }
                val maskAllSetValues = Array(maskCount) { -1 }
                val useDefaultsConstructor =
                    components.filterIsInstance<ParameterComponent>().any {
                      it.parameter.hasDefault
                    }

                +irCall(moshiSymbols.jsonReader.getSimpleFunction("beginObject")!!).apply {
                  dispatchReceiver = irGet(readerParam)
                }
                +irWhile().apply {
                  condition =
                      irCall(moshiSymbols.jsonReader.getSimpleFunction("hasNext")!!).apply {
                        dispatchReceiver = irGet(readerParam)
                      }
                  body =
                      irBlock {
                        val nextName =
                            irTemporary(
                                irCall(moshiSymbols.jsonReader.getSimpleFunction("selectName")!!)
                                    .apply {
                                      dispatchReceiver = irGet(readerParam)
                                      putValueArgument(
                                          0,
                                          irGetField(
                                              irGet(dispatchReceiverParameter!!), optionsField))
                                    },
                                nameHint = "nextName")

                        // We track property index and mask index separately, because mask index is
                        // based on _all_
                        // constructor arguments, while property index is only based on the index
                        // passed into
                        // JsonReader.Options.
                        var propertyIndex = 0

                        //
                        // Track important indices for masks. Masks generally increment with each
                        // parameter (including
                        // transient).
                        //
                        // Mask name index is an index into the maskNames array we initialized
                        // above.
                        //
                        // Once the maskIndex reaches 32, we've filled up that mask and have to move
                        // to the next mask
                        // name. Reset the maskIndex relative to here and continue incrementing.
                        //
                        var maskIndex = 0
                        var maskNameIndex = 0
                        val updateMaskIndexes = {
                          maskIndex++
                          if (maskIndex == 32) {
                            // Move to the next mask
                            maskIndex = 0
                            maskNameIndex++
                          }
                        }

                        val branches = buildList {
                          for (input in components) {
                            if (input is ParameterOnly ||
                                (input is ParameterProperty && input.property.isTransient)) {
                              updateMaskIndexes()
                              //                        constructorPropertyTypes +=
                              // input.type.asTypeBlock()
                              continue
                            } else if (input is PropertyOnly && input.property.isTransient) {
                              continue
                            }

                            // We've removed all parameter-only types by this point
                            val property = (input as PropertyComponent).property

                            // Proceed as usual
                            add(
                                irBranch(
                                    irEquals(irGet(nextName), irInt(propertyIndex)),
                                    result =
                                        irBlock {
                                          val result =
                                              irTemporary(
                                                  irCall(
                                                      moshiSymbols.jsonAdapter.getSimpleFunction(
                                                          "fromJson")!!)
                                                      .apply {
                                                        dispatchReceiver =
                                                            irGetField(
                                                                irGet(dispatchReceiverParameter!!),
                                                                adapterProperties.getValue(
                                                                    property.delegateKey))
                                                        putValueArgument(0, irGet(readerParam))
                                                      })
                                          if (!property.delegateKey.nullable) {
                                            // Check for unexpected nulls
                                            +irIfThen(
                                                irEqualsNull(irGet(result)),
                                                irThrow(
                                                    irCall(
                                                        moshiSymbols.moshiUtil.getSimpleFunction(
                                                            "unexpectedNull")!!)
                                                        .apply {
                                                          putValueArgument(
                                                              0, irString(property.localName))
                                                          putValueArgument(
                                                              1, irString(property.jsonName))
                                                          putValueArgument(2, irGet(readerParam))
                                                        }))
                                          }
                                          +irSet(
                                              localVars.getValue(property.localName), irGet(result))

                                          if (property.hasLocalIsPresentName) {
                                            // Presence tracker for a mutable property
                                            +irSet(
                                                localVars.getValue(property.localIsPresentName),
                                                irBoolean(true))
                                          }
                                          if (property.hasConstructorDefault) {
                                            // Track our mask index
                                            val inverted = (1 shl maskIndex).inv()
                                            if (input is ParameterComponent &&
                                                input.parameter.hasDefault) {
                                              maskAllSetValues[maskNameIndex] =
                                                  maskAllSetValues[maskNameIndex] and inverted
                                            }
                                            // bitMask[i] = bitMask[i] and inverted
                                            val and =
                                                irBinOp(
                                                    pluginContext,
                                                    OperatorNameConventions.AND,
                                                    irGet(bitMasks[maskNameIndex]),
                                                    irInt(inverted))
                                            +irSet(bitMasks[maskNameIndex].symbol, and)
                                          }
                                        }))
                            propertyIndex++
                            updateMaskIndexes()
                          }
                          // final else/-1
                          add(
                              irBranch(
                                  irEquals(irGet(nextName), irInt(-1)),
                                  result =
                                      irBlock {
                                        +irCall(
                                            moshiSymbols.jsonReader.getSimpleFunction("skipName")!!)
                                            .apply { dispatchReceiver = irGet(readerParam) }
                                        +irCall(
                                            moshiSymbols.jsonReader.getSimpleFunction(
                                                "skipValue")!!)
                                            .apply { dispatchReceiver = irGet(readerParam) }
                                      }))
                          // TODO merge this and the -1? Throw an error?
                          add(irElseBranch(irBlock {}))
                        }
                        +irWhen(pluginContext.irBuiltIns.intType, branches)
                      }
                }
                +irCall(moshiSymbols.jsonReader.getSimpleFunction("endObject")!!).apply {
                  dispatchReceiver = irGet(readerParam)
                }

                val constructor =
                    if (useDefaultsConstructor) {
                      // We can't get the synthetic constructor from here but we _can_ make a fake
                      // one to compile against
                      if (target.irClass.isInline &&
                          target.constructor.parameters.values.first().hasDefault) {
                        // TODO report this properly
                        error(
                            "value classes with default values are not currently supported in Moshi code gen")
                      }
                      target
                          .constructor
                          .irConstructor
                          .deepCopyWithVariables()
                          .apply {
                            parent = target.irClass
                            repeat(maskCount) {
                              addValueParameter("mask$it", pluginContext.irBuiltIns.intType)
                            }
                            addValueParameter(
                                "marker", irType("kotlin.jvm.internal.DefaultConstructorMarker"))
                          }
                          .symbol
                    } else {
                      target.constructor.irConstructor.symbol
                    }

                // TODO we could accumulate the missing properties instead
                for (input in components.filterIsInstance<PropertyComponent>()) {
                  val property = input.property
                  if (!property.isTransient && property.isRequired) {
                    +irIfThen(
                        condition = irEqualsNull(irGet(localVars.getValue(property.localName))),
                        thenPart =
                            irThrow(
                                irCall(
                                    moshiSymbols.moshiUtil.getSimpleFunction("missingProperty")!!)
                                    .apply {
                                      putValueArgument(0, irString(property.localName))
                                      putValueArgument(1, irString(property.jsonName))
                                      putValueArgument(2, irGet(readerParam))
                                    }))
                  }
                }
                val result =
                    irTemporary(
                        irCall(constructor).apply {
                          var lastIndex = 0
                          for (input in components.filterIsInstance<ParameterComponent>()) {
                            lastIndex = input.parameter.index
                            if (useDefaultsConstructor) {
                              if (input is ParameterOnly ||
                                  (input is ParameterProperty && input.property.isTransient)) {
                                // We have to use the default primitive for the available type in
                                // order  for invokeDefaultConstructor to properly invoke it. Just
                                // using "null" isn't safe because the transient type may be a
                                // primitive type. Inline a little comment for readability
                                // indicating which parameter is it's referring to
                                putValueArgument(
                                    input.parameter.index,
                                    defaultPrimitiveValue(input.type, pluginContext))
                              } else {
                                putValueArgument(
                                    input.parameter.index,
                                    irGet(
                                        localVars.getValue(
                                            (input as ParameterProperty).property.localName)))
                              }
                            } else if (input !is ParameterOnly) {
                              val property = (input as ParameterProperty).property
                              putValueArgument(
                                  input.parameter.index,
                                  irGet(localVars.getValue(property.localName)))
                            }
                          }

                          if (useDefaultsConstructor) {
                            // Add the masks and a null instance for the trailing default marker
                            // instance
                            for (mask in bitMasks) {
                              putValueArgument(++lastIndex, irGet(mask))
                            }
                            // DefaultConstructorMarker
                            putValueArgument(++lastIndex, irNull())
                          }
                        },
                        nameHint = "result",
                        irType = target.typeName)

                // Assign properties not present in the constructor.
                for (property in nonTransientProperties) {
                  if (property.hasConstructorParameter) {
                    continue // Property already handled.
                  }
                  val condition =
                      if (property.hasLocalIsPresentName) {
                        irGet(localVars.getValue(property.localIsPresentName))
                      } else {
                        irNot(irEqualsNull(irGet(localVars.getValue(property.localName))))
                      }
                  +irIfThen(
                      condition,
                      irCall(property.target.property.setter!!).apply {
                        dispatchReceiver = irGet(result)
                        putValueArgument(0, irGet(localVars.getValue(property.localName)))
                      })
                }

                +irReturn(irGet(result))
              }
        }
  }

  private fun IrBuilderWithScope.generateJsonAdapterSuperConstructorCall():
      IrDelegatingConstructorCall {
    return IrDelegatingConstructorCallImpl.fromSymbolOwner(
        startOffset,
        endOffset,
        pluginContext.irBuiltIns.unitType,
        moshiSymbols.jsonAdapter.constructors.single())
  }
}

/** Represents a prepared adapter with its [adapterClass]. */
internal data class PreparedAdapter(val adapterClass: IrClass)

private interface PropertyComponent {
  val property: PropertyGenerator
  val type: IrType
}

private interface ParameterComponent {
  val parameter: TargetParameter
  val type: IrType
}

/**
 * Type hierarchy for describing fromJson() components. Specifically - parameters, properties, and
 * parameter properties. All three of these scenarios participate in fromJson() parsing.
 */
private sealed class FromJsonComponent {

  abstract val type: IrType

  data class ParameterOnly(override val parameter: TargetParameter) :
      FromJsonComponent(), ParameterComponent {
    override val type: IrType = parameter.type
  }

  data class PropertyOnly(override val property: PropertyGenerator) :
      FromJsonComponent(), PropertyComponent {
    override val type: IrType = property.target.type
  }

  data class ParameterProperty(
      override val parameter: TargetParameter,
      override val property: PropertyGenerator
  ) : FromJsonComponent(), ParameterComponent, PropertyComponent {
    override val type: IrType = parameter.type
  }
}
