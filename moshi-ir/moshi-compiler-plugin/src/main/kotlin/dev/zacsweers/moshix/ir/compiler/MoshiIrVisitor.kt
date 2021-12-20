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

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irThrow
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageUtil
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.declarations.buildValueParameter
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irBranch
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irElseBranch
import org.jetbrains.kotlin.ir.builders.irEquals
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irIfNull
import org.jetbrains.kotlin.ir.builders.irImplicitCast
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irReturnUnit
import org.jetbrains.kotlin.ir.builders.irSet
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.builders.irWhen
import org.jetbrains.kotlin.ir.builders.irWhile
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin.INSTANCE_RECEIVER
import org.jetbrains.kotlin.ir.declarations.IrFactory
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrDelegatingConstructorCall
import org.jetbrains.kotlin.ir.expressions.impl.IrDelegatingConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeArgument
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.classifierOrFail
import org.jetbrains.kotlin.ir.types.createType
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.isMarkedNullable
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.ir.util.getSimpleFunction
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.source.getPsi

internal const val LOG_PREFIX = "*** MOSHI (IR):"
private val JSON_ANNOTATION = FqName("com.squareup.moshi.Json")
private val JSON_CLASS_ANNOTATION = FqName("com.squareup.moshi.JsonClass")

internal data class GeneratedAdapter(val adapterClass: IrDeclaration, val irFile: IrFile)

@OptIn(ObsoleteDescriptorBasedAPI::class)
internal class MoshiIrVisitor(
    private val moduleFragment: IrModuleFragment,
    private val pluginContext: IrPluginContext,
    private val messageCollector: MessageCollector,
    private val deferredAddedClasses: MutableList<GeneratedAdapter>
) : IrElementTransformerVoidWithContext() {

  private val moshiSymbols = MoshiSymbols(pluginContext.irBuiltIns, moduleFragment, pluginContext)

  private class Property(
      val property: IrProperty,
      val isIgnored: Boolean,
      val parameter: IrValueParameter?,
      val type: IrType,
  ) {
    val name = parameter?.name ?: property.name
    val jsonName: String = parameter?.jsonName() ?: property.jsonName() ?: name.asString()
    val hasDefault = parameter == null || parameter.defaultValue != null
  }

  override fun visitClassNew(declaration: IrClass): IrStatement {
    declaration.getAnnotation(JSON_CLASS_ANNOTATION)?.let { call ->
      call.getValueArgument(0)?.let { argument ->
        // This is generateAdapter
        @Suppress("UNCHECKED_CAST")
        if (!(argument as IrConst<Boolean>).value) {
          return super.visitClassNew(declaration)
        }
        // TODO check generator
        // TODO check modifiers/class types
        val primaryConstructor = declaration.primaryConstructor
        if (primaryConstructor == null) {
          declaration.reportError("Primary constructor is required on ${declaration.kotlinFqName}")
          return super.visitClassNew(declaration)
        }

        // TODO filter out params
        // TODO param-only requires default value
        val constructorParameters =
            primaryConstructor.valueParameters.associateBy { it.name.asString() }

        val properties = mutableListOf<Property>()
        for (prop in declaration.properties) {
          val parameter = constructorParameters[prop.name.asString()]
          properties +=
              Property(prop, isIgnored = false, parameter, type = parameter?.type ?: prop.type)
        }

        pluginContext.irFactory.run {
          val adapterClass = buildAdapterClass(declaration, properties)
          println("Dumping current IR src")
          println(adapterClass.dumpSrc())
          deferredAddedClasses += GeneratedAdapter(adapterClass, declaration.file)
        }
        // TODO Generate class
        // TODO generate adapter fields + constructor
        // TODO fromJson
        // TODO toJson
      }
    }
    return super.visitClassNew(declaration)
  }

  private fun IrFactory.buildAdapterClass(
      declaration: IrClass,
      properties: List<Property>
  ): IrClass {
    val adapterCls = buildClass {
      // TODO make name lookups work for nested?
      name = Name.identifier("${declaration.name.asString()}JsonAdapter")
      modality = Modality.FINAL
      kind = ClassKind.CLASS
      visibility = declaration.visibility // always public or internal
    }

    val isGeneric = declaration.typeParameters.isNotEmpty()
    adapterCls.typeParameters = declaration.typeParameters

    val adapterReceiver =
        buildValueParameter(adapterCls) {
          name = Name.special("<this>")
          type =
              IrSimpleTypeImpl(
                  classifier = adapterCls.symbol,
                  hasQuestionMark = false,
                  arguments = emptyList(),
                  annotations = emptyList())
          origin = INSTANCE_RECEIVER
        }
    adapterCls.thisReceiver = adapterReceiver
    adapterCls.superTypes =
        listOf(
            irType("com.squareup.moshi.JsonAdapter")
                .classifierOrFail
                .typeWith(declaration.defaultType))

    // TODO add check for types size
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
    }

    val optionsField =
        adapterCls
            .addField {
              name = Name.identifier("options")
              type = irType("com.squareup.moshi.JsonReader.Options")
              visibility = DescriptorVisibilities.PRIVATE
              isFinal = true
            }
            .apply {
              initializer =
                  pluginContext.createIrBuilder(symbol).run {
                    val functionRef =
                        pluginContext.referenceClass(
                                FqName("com.squareup.moshi.JsonReader.Options"))!!
                            .functions.single { it.descriptor.name.asString() == "of" }
                    irExprBody(
                        irCall(functionRef).apply {
                          val args = properties.map { irString(it.jsonName) }
                          this.putValueArgument(
                              0, irVararg(pluginContext.irBuiltIns.stringType, args))
                        })
                  }
            }

    // Each adapter based on property
    // TODO reuse adapters for same type
    // TODO qualifiers
    // TODO handle generics
    val propertiesByType = properties.groupBy { it.property.type }
    val adapterProperties = mutableMapOf<IrType, IrField>()
    for ((propertyType, props) in propertiesByType) {
      var genericIndex = -1
      val simpleName =
          when (val classifier = propertyType.classifierOrFail) {
            is IrTypeParameterSymbol -> {
              classifier.descriptor.name.asString().also { typeParamName ->
                genericIndex =
                    adapterCls.typeParameters.indexOfFirst { it.name.asString() == typeParamName }
              }
            }
            is IrClassSymbol -> {
              propertyType.classFqName!!.asString().replace(".", "_")
            }
            else -> {
              error("Unexpected type: $propertyType")
            }
          }
      val nullablePrefix = if (propertyType.isMarkedNullable()) "Nullable" else ""
      val field =
          adapterCls
              .addField {
                name = Name.identifier("${simpleName}${nullablePrefix}Adapter")
                type = moshiSymbols.jsonAdapter.typeWith(propertyType)
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
                                it.descriptor.name.asString() == "adapter" &&
                                    it.descriptor.valueParameters.size == 3
                              })
                              .apply {
                                dispatchReceiver = irGet(ctor.valueParameters[0])

                                // type
                                if (genericIndex == -1) {
                                  // Use typeOf() intrinsic
                                  // TODO maaaaaay have to use Types.newParameterizedType instead
                                  putValueArgument(
                                      0,
                                      irCall(
                                          pluginContext
                                              .referenceProperties(
                                                  FqName("kotlin.reflect.javaType"))
                                              .first()
                                              .owner
                                              .getter!!)
                                          .apply {
                                            extensionReceiver =
                                                irCall(
                                                    pluginContext
                                                        .referenceFunctions(
                                                            FqName("kotlin.reflect.typeOf"))
                                                        .first())
                                                    .apply { putTypeArgument(0, propertyType) }
                                          })
                                } else {
                                  // It's generic, get from types array
                                  val arrayGet =
                                      pluginContext.irBuiltIns.arrayClass.owner.declarations
                                          .filterIsInstance<IrSimpleFunction>()
                                          .single { it.name.asString() == "get" }
                                  putValueArgument(
                                      0,
                                      irCall(arrayGet.symbol).apply {
                                        dispatchReceiver = irGet(ctor.valueParameters[1])
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
                                                FqName("kotlin.collections.emptySet"))
                                            .first())
                                        .apply { putTypeArgument(0, irType("kotlin.Annotation")) })
                                // field hint
                                putValueArgument(2, irString(props.first().jsonName))
                              })
                    }
              }
      adapterProperties[propertyType] = field
    }

    adapterCls
        .addOverride(
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
                // TODO create local vars for all properties
                val localVars = mutableMapOf<String, IrVariable>()
                for (property in properties) {
                  // TODO primitives need diff defaults
                  val name = property.name.asString()
                  val localVar =
                      irTemporary(irNull(property.type), isMutable = true, nameHint = name)
                  localVars[name] = localVar
                }
                +irCall(moshiSymbols.jsonReader.getSimpleFunction("beginObject")!!).apply {
                  dispatchReceiver = irGet(readerParam)
                }
                // TODO implement body
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
                        val branches = buildList {
                          for ((index, prop) in properties.withIndex()) {
                            add(
                                irBranch(
                                    irEquals(irGet(nextName), irInt(index)),
                                    result =
                                        irBlock {
                                          // TODO check for unexpected null
                                          +irSet(
                                              localVars.getValue(prop.name.asString()),
                                              irCall(
                                                  moshiSymbols.jsonAdapter.getSimpleFunction(
                                                      "fromJson")!!)
                                                  .apply {
                                                    dispatchReceiver =
                                                        irGetField(
                                                            irGet(dispatchReceiverParameter!!),
                                                            adapterProperties.getValue(prop.type))
                                                    putValueArgument(0, irGet(readerParam))
                                                  })
                                        }))
                          }
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

                // TODO return the constructed type
                // TODO use defaults
                // TODO check missing properties
                // TODO primitives can't be nullable local vars, otherwise bytecode is unhappy here
                +irReturn(
                    irCall(declaration.primaryConstructor!!.symbol).apply {
                      for ((index, prop) in properties.withIndex()) {
                        putValueArgument(index, irGet(localVars.getValue(prop.name.asString())))
                      }
                    })
              }
        }

    adapterCls
        .addOverride(
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
                // TODO can we use IMPLICIT_NOTNULL here and just skip the null check?
                +irIfNull(
                    value.type,
                    irGet(value),
                    irThrow(
                        irCall(
                            // TODO why can't I use kotlin.NullPointerException here?
                            pluginContext.referenceClass(
                                    FqName("kotlin.KotlinNullPointerException"))!!
                                .constructors.first { it.descriptor.valueParameters.size == 1 })
                            .apply {
                              putValueArgument(
                                  0,
                                  irString(
                                      "value was null! Wrap in .nullSafe() to write nullable values."))
                            }),
                    irBlock {})
                // Cast it up to the actual type
                val castValue =
                    irTemporary(
                        irImplicitCast(irGet(value), declaration.defaultType.makeNotNull()),
                        "castValue")
                +irCall(moshiSymbols.jsonWriter.getSimpleFunction("beginObject")!!).apply {
                  dispatchReceiver = irGet(writer)
                }
                for (property in properties) {
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
                            adapterProperties.getValue(property.type))
                    // writer
                    putValueArgument(0, irGet(writer))
                    // value.prop
                    putValueArgument(
                        1,
                        irCall(declaration.getPropertyGetter(property.name.asString())!!).apply {
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

    // TODO toString()

    return adapterCls
  }

  private fun irType(
      qualifiedName: String,
      nullable: Boolean = false,
      arguments: List<IrTypeArgument> = emptyList()
  ): IrType =
      pluginContext.referenceClass(FqName(qualifiedName))!!.createType(
          hasQuestionMark = nullable, arguments = arguments)

  private fun IrFunction.reflectivelySetFakeOverride(isFakeOverride: Boolean) {
    with(javaClass.getDeclaredField("isFakeOverride")) {
      isAccessible = true
      setBoolean(this@reflectivelySetFakeOverride, isFakeOverride)
    }
  }

  /**
   * Only works properly after [IrFunction.mutateWithNewDispatchReceiverParameterForParentClass] has
   * been called on [irFunction].
   */
  private fun IrBlockBodyBuilder.receiver(irFunction: IrFunction) =
      IrGetValueImpl(irFunction.dispatchReceiverParameter!!)

  private fun IrBlockBodyBuilder.IrGetValueImpl(irParameter: IrValueParameter) =
      IrGetValueImpl(startOffset, endOffset, irParameter.type, irParameter.symbol)

  private fun log(message: String) {
    messageCollector.report(CompilerMessageSeverity.LOGGING, "$LOG_PREFIX $message")
  }

  private fun IrPluginContext.createIrBuilder(symbol: IrSymbol): DeclarationIrBuilder {
    return DeclarationIrBuilder(this, symbol, symbol.owner.startOffset, symbol.owner.endOffset)
  }

  private fun IrClass.reportError(message: String) {
    val location = MessageUtil.psiElementToMessageLocation(descriptor.source.getPsi())
    messageCollector.report(CompilerMessageSeverity.ERROR, "$LOG_PREFIX $message", location)
  }

  fun IrBuilderWithScope.generateJsonAdapterSuperConstructorCall(): IrDelegatingConstructorCall {
    val anyConstructor =
        pluginContext.referenceClass(FqName("com.squareup.moshi.JsonAdapter"))!!.owner.declarations
            .single { it is IrConstructor } as
            IrConstructor
    return IrDelegatingConstructorCallImpl.fromSymbolDescriptor(
        startOffset, endOffset, pluginContext.irBuiltIns.unitType, anyConstructor.symbol)
  }

  private fun IrClass.addOverride(
      baseFqName: FqName,
      name: String,
      returnType: IrType,
      modality: Modality = Modality.FINAL,
      overloadFilter: (function: IrSimpleFunction) -> Boolean = { true }
  ): IrSimpleFunction =
      addFunction(name, returnType, modality).apply {
        overriddenSymbols =
            superTypes
                .mapNotNull { superType ->
                  superType.classOrNull?.owner?.takeIf { superClass ->
                    superClass.isSubclassOfFqName(baseFqName.asString())
                  }
                }
                .flatMap { superClass ->
                  superClass
                      .functions
                      .filter { function ->
                        function.name.asString() == name &&
                            function.overridesFunctionIn(baseFqName) &&
                            overloadFilter(function)
                      }
                      .map { it.symbol }
                      .toList()
                }
      }
}

private fun IrAnnotationContainer.jsonName(): String? {
  @Suppress("UNCHECKED_CAST")
  return (getAnnotation(JSON_ANNOTATION)?.getValueArgument(0) as? IrConst<String>?)?.value
}

private val IrProperty.type: IrType
  get() =
      getter?.returnType
          ?: setter?.valueParameters?.first()?.type ?: backingField?.type
              ?: error("No type for property $name")
