package dev.zacsweers.moshix.ir.compiler.sealed

import dev.zacsweers.moshix.ir.compiler.MoshiSymbols
import dev.zacsweers.moshix.ir.compiler.api.AdapterGenerator
import dev.zacsweers.moshix.ir.compiler.api.PreparedAdapter
import dev.zacsweers.moshix.ir.compiler.labelKey
import dev.zacsweers.moshix.ir.compiler.util.addOverride
import dev.zacsweers.moshix.ir.compiler.util.checkIsVisible
import dev.zacsweers.moshix.ir.compiler.util.createIrBuilder
import dev.zacsweers.moshix.ir.compiler.util.error
import dev.zacsweers.moshix.ir.compiler.util.generateToStringFun
import dev.zacsweers.moshix.ir.compiler.util.irConstructorBody
import dev.zacsweers.moshix.ir.compiler.util.irInstanceInitializerCall
import dev.zacsweers.moshix.ir.compiler.util.irInvoke
import dev.zacsweers.moshix.ir.compiler.util.irType
import dev.zacsweers.moshix.ir.compiler.util.rawType
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
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
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrDelegatingConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.expressions.impl.IrDelegatingConstructorCallImpl
import org.jetbrains.kotlin.ir.interpreter.hasAnnotation
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classifierOrFail
import org.jetbrains.kotlin.ir.types.createType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.getSimpleFunction
import org.jetbrains.kotlin.ir.util.isObject
import org.jetbrains.kotlin.ir.util.packageFqName
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal class SealedAdapterGenerator
private constructor(
  private val pluginContext: IrPluginContext,
  private val logger: MessageCollector,
  private val moshiSymbols: MoshiSymbols,
  private val moshiSealedSymbols: MoshiSealedSymbols,
  private val target: IrClass,
  private val labelKey: String,
) : AdapterGenerator {
  override fun prepare(): PreparedAdapter? {
    // If this is a nested sealed type of a moshi-sealed parent, defer to the parent
    val sealedParent =
      if (target.hasAnnotation(FqName("dev.zacsweers.moshix.sealed.annotations.NestedSealed"))) {
        target.superTypes.firstNotNullOfOrNull { supertype ->
          pluginContext
            .referenceClass(supertype.getClass()!!.classId!!)!!
            .owner
            .getAnnotation(FqName("com.squareup.moshi.JsonClass"))
            ?.labelKey()
            ?.let { supertype to it }
        }
          ?: error(
            "No JsonClass-annotated sealed supertype found for ${target.fqNameWhenAvailable}"
          )
      } else {
        null
      }

    sealedParent?.let { (_, parentLabelKey) ->
      if (parentLabelKey == labelKey) {
        logger.error(target) {
          "@NestedSealed-annotated subtype ${target.fqNameWhenAvailable} is inappropriately annotated with @JsonClass(generator = " +
            "\"sealed:$labelKey\")."
        }
        return null
      }
    }

    var fallbackStrategy: FallbackStrategy? = null

    val useDefaultNull =
      target.hasAnnotation(FqName("dev.zacsweers.moshix.sealed.annotations.DefaultNull"))

    val fallbackAdapterAnnotation =
      target.getAnnotation(FqName("dev.zacsweers.moshix.sealed.annotations.FallbackJsonAdapter"))

    if (useDefaultNull && (fallbackAdapterAnnotation != null)) {
      logger.error(target) {
        "Only one of @DefaultNull or @FallbackJsonAdapter can be used at a time"
      }
      return null
    }

    if (fallbackAdapterAnnotation != null) {
      val adapterType = fallbackAdapterAnnotation.getValueArgument(0) as IrClassReference
      // TODO can we check adapter type is valid? Compiler will check it for us
      val adapterDeclaration = adapterType.classType.rawType()
      val constructor =
        adapterDeclaration.primaryConstructor
          ?: run {
            logger.error(adapterDeclaration) {
              "No primary constructor found for fallback adapter ${adapterDeclaration.fqNameWhenAvailable}"
            }
            return null
          }
      constructor.visibility.checkIsVisible()
      val hasMoshiParam =
        when (constructor.valueParameters.size) {
          0 -> {
            // Nothing to do
            false
          }
          1 -> {
            // Check it's a Moshi parameter
            val moshiParam = constructor.valueParameters[0]
            // TODO can this be simpler?
            if (moshiSymbols.moshi != moshiParam.type) {
              logger.error(target) {
                "Fallback adapter type's primary constructor can only have a Moshi parameter"
              }
              return null
            }
            true
          }
          else -> {
            logger.error(target) {
              "Fallback adapter type's primary constructor can only have a Moshi parameter"
            }
            return null
          }
        }
      fallbackStrategy =
        FallbackStrategy.FallbackAdapter(
          targetConstructor = constructor.symbol,
          hasMoshiParam = hasMoshiParam
        )
    } else if (useDefaultNull) {
      fallbackStrategy = FallbackStrategy.Null
    }

    val objectSubtypes = mutableListOf<IrClass>()
    val seenLabels = mutableMapOf<String, IrClass>()
    var hasErrors = false
    val sealedSubtypes =
      target.sealedSubclasses
        .map { it.owner }
        .flatMapTo(LinkedHashSet()) { subtype ->
          val isObject = subtype.kind == ClassKind.OBJECT
          if (
            isObject &&
              subtype.hasAnnotation(FqName("dev.zacsweers.moshix.sealed.annotations.DefaultObject"))
          ) {
            if (useDefaultNull) {
              // Print both for reference
              logger.error(subtype) {
                """
                      Cannot have both @DefaultNull and @DefaultObject. @DefaultObject type: ${target.fqNameWhenAvailable}
                      Cannot have both @DefaultNull and @DefaultObject. @DefaultNull type: ${subtype.fqNameWhenAvailable}
                    """
                  .trimIndent()
              }
              hasErrors = true
              return@flatMapTo emptySequence()
            } else {
              return@flatMapTo sequenceOf(Subtype.ObjectType(subtype))
            }
          } else {
            walkTypeLabels(target, subtype, labelKey, seenLabels, objectSubtypes)
          }
        }

    if (hasErrors) {
      return null
    }

    pluginContext.irFactory.run {
      return createType(
        targetType = target,
        labelKey = labelKey,
        fallbackStrategy = fallbackStrategy,
        generatedAnnotation = null,
        subtypes = sealedSubtypes,
        objectSubtypes = objectSubtypes
      )
    }
  }

  private fun walkTypeLabels(
    rootType: IrClass,
    subtype: IrClass,
    labelKey: String,
    seenLabels: MutableMap<String, IrClass>,
    objectSubtypes: MutableList<IrClass>,
  ): Sequence<Subtype> {
    // If it's sealed, check if it's inheriting from our existing type or a separate/new branching
    // off point
    return if (subtype.modality == Modality.SEALED) {
      val nestedLabelKey =
        subtype
          .getAnnotation(FqName("com.squareup.moshi.JsonClass"))
          ?.labelKey(checkGenerateAdapter = false)
      if (nestedLabelKey != null) {
        // Redundant case
        if (labelKey == nestedLabelKey) {
          logger.error(subtype) {
            "Sealed subtype $subtype is redundantly annotated with @JsonClass(generator = " +
              "\"sealed:$nestedLabelKey\")."
          }
          return emptySequence()
        }
      }

      if (
        subtype.getAnnotation(FqName("dev.zacsweers.moshix.sealed.annotations.TypeLabel")) != null
      ) {
        // It's a different type, allow it to be used as a label and branch off from here.
        val classType =
          addLabelKeyForType(subtype, seenLabels, objectSubtypes, skipJsonClassCheck = true)
        classType?.let { sequenceOf(it) } ?: emptySequence()
      } else {
        // Recurse, inheriting the top type
        subtype.sealedSubclasses.asSequence().flatMap {
          walkTypeLabels(
            rootType = rootType,
            subtype = it.owner,
            labelKey = labelKey,
            seenLabels = seenLabels,
            objectSubtypes = objectSubtypes
          )
        }
      }
    } else {
      val classType =
        addLabelKeyForType(
          subtype = subtype,
          seenLabels = seenLabels,
          objectSubtypes = objectSubtypes
        )
      classType?.let { sequenceOf(it) } ?: emptySequence()
    }
  }

  private fun addLabelKeyForType(
    subtype: IrClass,
    seenLabels: MutableMap<String, IrClass>,
    objectSubtypes: MutableList<IrClass>,
    skipJsonClassCheck: Boolean = false
  ): Subtype? {
    // Regular subtype, read its label
    val labelAnnotation =
      subtype.getAnnotation(FqName("dev.zacsweers.moshix.sealed.annotations.TypeLabel"))
        ?: run {
          logger.error(subtype) { "Missing @TypeLabel" }
          return null
        }

    if (subtype.typeParameters.isNotEmpty()) {
      logger.error(subtype) { "Moshi-sealed subtypes cannot be generic." }
      return null
    }

    val labels = mutableListOf<String>()

    @Suppress("UNCHECKED_CAST")
    val mainLabel =
      (labelAnnotation.getValueArgument(0) as? IrConst<String>)?.value
        ?: run {
          logger.error(subtype) { "No label member for TypeLabel annotation!" }
          return null
        }

    seenLabels.put(mainLabel, subtype)?.let { prev ->
      logger.error(target) {
        "Duplicate label '$mainLabel' defined for ${subtype.fqNameWhenAvailable} and ${prev.fqNameWhenAvailable}."
      }
      return null
    }

    if (!skipJsonClassCheck) {
      val labelKey = subtype.getAnnotation(FqName("com.squareup.moshi.JsonClass"))?.labelKey()
      if (labelKey != null) {
        logger.error(subtype) {
          "Sealed subtype $subtype is annotated with @JsonClass(generator = \"sealed:$labelKey\") and @TypeLabel."
        }
        return null
      }
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
        return null
      }
    }

    labels += alternates

    if (subtype.isObject) {
      objectSubtypes += subtype
    }

    return Subtype.ClassType(subtype, labels)
  }

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
            annotations = emptyList()
          )
        origin = IrDeclarationOrigin.INSTANCE_RECEIVER
      }
    adapterCls.thisReceiver = adapterReceiver
    val jsonAdapterType =
      pluginContext
        .irType(ClassId.fromString("com/squareup/moshi/JsonAdapter"))
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
                    0,
                    moshiSymbols.javaClassReference(this@run, targetType.defaultType)
                  )
                  putValueArgument(1, irString(labelKey))
                }

              val moshiParam = ctor.valueParameters[0]
              val moshiAccess: IrExpression =
                if (hasObjectSubtypes) {
                  val initial =
                    irCall(moshiSymbols.moshiNewBuilder).apply {
                      dispatchReceiver = irGet(moshiParam)
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
                          }
                        )
                      }
                    }
                  irCall(moshiSymbols.moshiBuilderBuild).apply { dispatchReceiver = newBuilds }
                } else {
                  irGet(moshiParam)
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
                        moshiSymbols.javaClassReference(this@run, subtype.className.defaultType)
                      )
                      putValueArgument(1, irString(label))
                    }
                  }
                }

              var finalFallbackStrategy = fallbackStrategy
              subtypes.filterIsInstance<Subtype.ObjectType>().firstOrNull()?.let { defaultObject ->
                if (fallbackStrategy == null) {
                  finalFallbackStrategy =
                    FallbackStrategy.DefaultObject(defaultObject.className.symbol)
                } else {
                  logger.error(target) {
                    "Only one of @DefaultObject, @DefaultNull, or @FallbackJsonAdapter can be used at a time."
                  }
                }
              }

              val possiblyWithDefaultExpression =
                finalFallbackStrategy?.let {
                  it.statement(
                    builder = this,
                    moshiSealedSymbols = moshiSealedSymbols,
                    subtypesExpression = subtypesExpression,
                    targetType = targetType.defaultType,
                    moshiParam = moshiParam
                  )
                }
                  ?: subtypesExpression

              // .create(Message::class.java, emptySet(), moshi) as JsonAdapter<Message>
              irExprBody(
                irAs(
                  irCall(moshiSymbols.jsonAdapterFactoryCreate).apply {
                    dispatchReceiver = possiblyWithDefaultExpression
                    putValueArgument(
                      0,
                      moshiSymbols.javaClassReference(this@run, targetType.defaultType)
                    )
                    putValueArgument(1, irCall(moshiSymbols.emptySet))
                    putValueArgument(2, moshiAccess)
                  },
                  jsonAdapterType
                )
              )
            }
        }

    adapterCls.generateFromJsonFun(runtimeAdapter)
    adapterCls.generateToJsonFun(runtimeAdapter)
    adapterCls.generateToStringFun(
      pluginContext,
      simpleNames.joinToString("."),
      generatedName = "GeneratedSealedJsonAdapter"
    )

    return PreparedAdapter(adapterCls)
  }

  private fun IrClass.generateToJsonFun(delegateField: IrField): IrFunction {
    return addOverride(
        FqName("com.squareup.moshi.JsonAdapter"),
        Name.identifier("toJson").identifier,
        pluginContext.irBuiltIns.unitType,
        modality = Modality.OPEN
      ) { function ->
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
                dispatchReceiver = irGetField(irGet(dispatchReceiverParameter!!), delegateField)
                putValueArgument(0, irGet(readerParam))
              }
            )
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
      moshiSymbols.jsonAdapter.constructors.single()
    )
  }

  companion object {
    operator fun invoke(
      pluginContext: IrPluginContext,
      logger: MessageCollector,
      moshiSymbols: MoshiSymbols,
      moshiSealedSymbols: MoshiSealedSymbols,
      target: IrClass,
      labelKey: String,
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
        pluginContext = pluginContext,
        logger = logger,
        moshiSymbols = moshiSymbols,
        moshiSealedSymbols = moshiSealedSymbols,
        target = target,
        labelKey = labelKey
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
          dispatchReceiver = subtypesExpression
          putValueArgument(0, irNull(targetType))
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
          dispatchReceiver = subtypesExpression
          // TODO cast to JsonAdapter<Any>
          val args =
            if (hasMoshiParam) {
              arrayOf(irGet(moshiParam))
            } else {
              emptyArray()
            }
          putValueArgument(0, irInvoke(callee = targetConstructor, args = args))
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
          dispatchReceiver = subtypesExpression
          putValueArgument(0, irGetObject(defaultObject))
        }
      }
    }
  }
}
