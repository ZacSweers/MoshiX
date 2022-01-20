package dev.zacsweers.moshix.ir.compiler.sealed

import dev.zacsweers.moshix.ir.compiler.MoshiSymbols
import dev.zacsweers.moshix.ir.compiler.api.AdapterGenerator
import dev.zacsweers.moshix.ir.compiler.api.PreparedAdapter
import dev.zacsweers.moshix.ir.compiler.util.addOverride
import dev.zacsweers.moshix.ir.compiler.util.createIrBuilder
import dev.zacsweers.moshix.ir.compiler.util.error
import dev.zacsweers.moshix.ir.compiler.util.irConstructorBody
import dev.zacsweers.moshix.ir.compiler.util.irInstanceInitializerCall
import dev.zacsweers.moshix.ir.compiler.util.irType
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.declarations.buildValueParameter
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
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFactory
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrDelegatingConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.expressions.impl.IrDelegatingConstructorCallImpl
import org.jetbrains.kotlin.ir.interpreter.hasAnnotation
import org.jetbrains.kotlin.ir.types.classifierOrFail
import org.jetbrains.kotlin.ir.types.createType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.getSimpleFunction
import org.jetbrains.kotlin.ir.util.packageFqName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

internal class SealedAdapterGenerator
private constructor(
    private val pluginContext: IrPluginContext,
    private val logger: MessageCollector,
    private val moshiSymbols: MoshiSymbols,
    private val moshiSealedSymbols: MoshiSealedSymbols,
    private val target: IrClass,
    private val typeLabel: String,
) : AdapterGenerator {
  @OptIn(ObsoleteDescriptorBasedAPI::class)
  override fun prepare(): PreparedAdapter? {
    // TODO there's no non-descriptor API for this yet
    val useDefaultNull =
        target.hasAnnotation(FqName("dev.zacsweers.moshix.sealed.annotations.DefaultNull"))
    val objectSubtypes = mutableListOf<IrClass>()
    val seenLabels = mutableMapOf<String, IrClass>()
    var hasErrors = false
    val sealedSubtypes =
        target.descriptor.sealedSubclasses
            .map { pluginContext.referenceClass(it.fqNameSafe)!!.owner }
            .mapNotNullTo(LinkedHashSet()) { subtype ->
              val isObject = subtype.kind == ClassKind.OBJECT
              if (isObject &&
                  subtype.hasAnnotation(
                      FqName("dev.zacsweers.moshix.sealed.annotations.DefaultObject"))) {
                if (useDefaultNull) {
                  // Print both for reference
                  logger.error(subtype) {
                    """
                      Cannot have both @DefaultNull and @DefaultObject. @DefaultObject type: ${target.fqNameWhenAvailable}
                      Cannot have both @DefaultNull and @DefaultObject. @DefaultNull type: ${subtype.fqNameWhenAvailable}
                    """.trimIndent()
                  }
                  hasErrors = true
                  return@mapNotNullTo null
                } else {
                  return@mapNotNullTo Subtype.ObjectType(subtype)
                }
              } else {
                val labelAnnotation =
                    subtype.getAnnotation(
                        FqName("dev.zacsweers.moshix.sealed.annotations.TypeLabel"))
                        ?: run {
                          logger.error(subtype) { "Missing @TypeLabel" }
                          hasErrors = true
                          return@mapNotNullTo null
                        }

                if (subtype.typeParameters.isNotEmpty()) {
                  logger.error(subtype) { "Moshi-sealed subtypes cannot be generic." }
                  hasErrors = true
                  return@mapNotNullTo null
                }

                val labels = mutableListOf<String>()

                @Suppress("UNCHECKED_CAST")
                val mainLabel =
                    (labelAnnotation.getValueArgument(0) as? IrConst<String>)?.value
                        ?: run {
                          logger.error(subtype) { "No label member for TypeLabel annotation!" }
                          hasErrors = true
                          return@mapNotNullTo null
                        }

                seenLabels.put(mainLabel, subtype)?.let { prev ->
                  logger.error(target) {
                    "Duplicate label '$mainLabel' defined for ${subtype.fqNameWhenAvailable} and ${prev.fqNameWhenAvailable}."
                  }
                  hasErrors = true
                  return@mapNotNullTo null
                }

                labels += mainLabel

                @Suppress("UNCHECKED_CAST")
                val alternates =
                    (labelAnnotation.getValueArgument(1) as IrVararg?)?.elements.orEmpty().map {
                      (it as IrConst<String>).value
                    }

                for (alternate in alternates) {
                  seenLabels.put(alternate, subtype)?.let { prev ->
                    logger.error(target) {
                      "Duplicate alternate label '$alternate' defined for ${subtype.fqNameWhenAvailable} and ${prev.fqNameWhenAvailable}."
                    }
                    hasErrors = true
                    return@mapNotNullTo null
                  }
                }

                labels += alternates

                if (isObject) {
                  objectSubtypes += subtype
                }
                Subtype.ClassType(subtype, labels)
              }
            }

    if (hasErrors) {
      return null
    }

    pluginContext.irFactory.run {
      return createType(
          targetType = target,
          typeLabel = typeLabel,
          useDefaultNull = useDefaultNull,
          generatedAnnotation = null,
          subtypes = sealedSubtypes,
          objectSubtypes = objectSubtypes)
    }
  }

  private fun IrFactory.createType(
      targetType: IrClass,
      typeLabel: String,
      useDefaultNull: Boolean,
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
    }

    adapterCls.origin = MoshiSealedOrigin

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
    val jsonAdapterType =
        pluginContext
            .irType("com.squareup.moshi.JsonAdapter")
            .classifierOrFail
            .typeWith(targetType.defaultType)
    adapterCls.superTypes = listOf(jsonAdapterType)

    val hasObjectSubtypes = objectSubtypes.isNotEmpty()
    val ctor = adapterCls.generateConstructor()

    val runtimeAdapter =
        adapterCls
            .addField {
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
                          putTypeArgument(0, targetType.defaultType)
                          putValueArgument(
                              0, moshiSymbols.javaClassReference(this@run, targetType.defaultType))
                          putValueArgument(1, irString(typeLabel))
                        }

                    val subtypesExpression =
                        subtypes.filterIsInstance<Subtype.ClassType>().fold(ofCreatorExpression) {
                            receiver,
                            subtype ->
                          subtype.labels.fold(receiver) { nestedReceiver, label ->
                            irCall(moshiSealedSymbols.pjafWithSubtype).apply {
                              dispatchReceiver = nestedReceiver
                              putValueArgument(
                                  0,
                                  moshiSymbols.javaClassReference(
                                      this@run, subtype.className.defaultType))
                              putValueArgument(1, irString(label))
                            }
                          }
                        }

                    val possiblyWithDefaultExpression =
                        if (useDefaultNull) {
                          irCall(moshiSealedSymbols.pjafWithDefaultValue).apply {
                            dispatchReceiver = subtypesExpression
                            putValueArgument(0, irNull(targetType.defaultType))
                          }
                        } else {
                          subtypes.filterIsInstance<Subtype.ObjectType>().firstOrNull()?.let {
                              defaultObject ->
                            irCall(moshiSealedSymbols.pjafWithDefaultValue).apply {
                              dispatchReceiver = subtypesExpression
                              putValueArgument(0, irGetObject(defaultObject.className.symbol))
                            }
                          }
                              ?: subtypesExpression
                        }

                    val moshiParam = irGet(ctor.valueParameters[0])
                    val moshiAccess: IrExpression =
                        if (hasObjectSubtypes) {
                          val initial =
                              irCall(moshiSymbols.moshiNewBuilder).apply {
                                dispatchReceiver = moshiParam
                              }
                          val newBuilds =
                              objectSubtypes.fold(initial) { receiver, subtype ->
                                irCall(moshiSymbols.addAdapter).apply {
                                  extensionReceiver = receiver
                                  putTypeArgument(0, subtype.defaultType)
                                  putValueArgument(
                                      0,
                                      irCall(moshiSealedSymbols.objectJsonAdapterCtor).apply {
                                        putValueArgument(0, irGetObject(subtype.symbol))
                                      })
                                }
                              }
                          irCall(moshiSymbols.moshiBuilderBuild).apply {
                            dispatchReceiver = newBuilds
                          }
                        } else {
                          moshiParam
                        }

                    // .create(Message::class.java, emptySet(), moshi) as JsonAdapter<Message>
                    irExprBody(
                        irAs(
                            irCall(moshiSymbols.jsonAdapterFactoryCreate).apply {
                              dispatchReceiver = possiblyWithDefaultExpression
                              putValueArgument(
                                  0,
                                  moshiSymbols.javaClassReference(this@run, targetType.defaultType))
                              putValueArgument(1, irCall(moshiSymbols.emptySet))
                              putValueArgument(2, moshiAccess)
                            },
                            jsonAdapterType))
                  }
            }

    adapterCls.generateFromJsonFun(runtimeAdapter)
    adapterCls.generateToJsonFun(runtimeAdapter)

    // TODO toString?

    return PreparedAdapter(adapterCls)
  }

  private fun IrClass.generateToJsonFun(delegateField: IrField): IrFunction {
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
                +irCall(moshiSymbols.jsonAdapter.getSimpleFunction("toJson")!!).apply {
                  dispatchReceiver = irGetField(irGet(dispatchReceiverParameter!!), delegateField)
                  putValueArgument(0, irGet(writer))
                  putValueArgument(1, irGet(value))
                }
                +irReturnUnit()
              }
        }
  }

  private fun IrClass.generateFromJsonFun(delegateField: IrField): IrFunction {
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
            type = moshiSymbols.jsonReader.defaultType
          }
          body =
              DeclarationIrBuilder(pluginContext, symbol).irBlockBody {
                +irReturn(
                    irCall(moshiSymbols.jsonAdapter.getSimpleFunction("fromJson")!!).apply {
                      dispatchReceiver =
                          irGetField(irGet(dispatchReceiverParameter!!), delegateField)
                      putValueArgument(0, irGet(readerParam))
                    })
              }
        }
  }

  private fun IrClass.generateConstructor(): IrConstructor {
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
                type = moshiSymbols.moshi.defaultType
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

    return ctor
  }

  private fun IrBuilderWithScope.generateJsonAdapterSuperConstructorCall():
      IrDelegatingConstructorCall {
    return IrDelegatingConstructorCallImpl.fromSymbolOwner(
        startOffset,
        endOffset,
        pluginContext.irBuiltIns.unitType,
        moshiSymbols.jsonAdapter.constructors.single())
  }

  companion object {
    operator fun invoke(
        pluginContext: IrPluginContext,
        logger: MessageCollector,
        moshiSymbols: MoshiSymbols,
        moshiSealedSymbols: MoshiSealedSymbols,
        target: IrClass,
        typeLabel: String,
    ): AdapterGenerator? {
      if (target.kind != ClassKind.CLASS && target.kind != ClassKind.INTERFACE) {
        logger.error(target) {
          "@JsonClass can't be applied to ${target.fqNameWhenAvailable}: must be a Kotlin class"
        }
        return null
      }
      if (target.modality != Modality.SEALED) {
        logger.error(target) { "Must be a sealed class!" }
        return null
      }
      return SealedAdapterGenerator(
          pluginContext, logger, moshiSymbols, moshiSealedSymbols, target, typeLabel)
    }
  }
}

private sealed class Subtype(val className: IrClass) {
  class ObjectType(className: IrClass) : Subtype(className)
  class ClassType(className: IrClass, val labels: List<String>) : Subtype(className)
}
