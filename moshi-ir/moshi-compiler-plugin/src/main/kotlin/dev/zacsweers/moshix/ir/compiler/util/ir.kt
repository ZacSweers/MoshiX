// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.ir.compiler.util

import dev.zacsweers.moshix.ir.compiler.MoshiDiagnostics
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.builtins.PrimitiveType
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrDiagnosticReporter
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.builders.irByte
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irChar
import org.jetbrains.kotlin.ir.builders.irConcat
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irLong
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irShort
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrMemberAccessExpression
import org.jetbrains.kotlin.ir.expressions.addArgument
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeArgument
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.createType
import org.jetbrains.kotlin.ir.types.getPrimitiveType
import org.jetbrains.kotlin.ir.types.isNothing
import org.jetbrains.kotlin.ir.types.isMarkedNullable
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.util.allOverridden
import org.jetbrains.kotlin.ir.util.erasedUpperBound
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.ir.util.toIrConst
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.util.OperatorNameConventions

private val JAVA_LANG_VOID = FqName("java.lang.Void")

internal inline fun IrDiagnosticReporter.error(message: () -> String) {
  report(MoshiDiagnostics.SOURCELESS_MOSHI_ERROR, message())
}

internal inline fun IrDiagnosticReporter.error(declaration: IrDeclaration, message: () -> String) {
  at(declaration).report(MoshiDiagnostics.MOSHI_ERROR, message())
}

internal inline fun IrDiagnosticReporter.error(
  element: IrElement,
  file: IrFile,
  message: () -> String,
) {
  at(element, file).report(MoshiDiagnostics.MOSHI_ERROR, message())
}

internal fun IrFunction.buildBlockBody(
  context: IrPluginContext,
  blockBody: IrBlockBodyBuilder.() -> Unit,
) {
  body = context.createIrBuilder(symbol).irBlockBody(body = blockBody)
}

internal fun IrClass.isSubclassOfFqName(fqName: String): Boolean =
  fqNameWhenAvailable?.asString() == fqName ||
    superTypes.any { it.erasedUpperBound.isSubclassOfFqName(fqName) }

internal fun IrSimpleFunction.overridesFunctionIn(fqName: FqName): Boolean =
  parentClassOrNull?.fqNameWhenAvailable == fqName ||
    allOverridden().any { it.parentClassOrNull?.fqNameWhenAvailable == fqName }

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal fun IrPluginContext.createIrBuilder(symbol: IrSymbol): DeclarationIrBuilder {
  return DeclarationIrBuilder(this, symbol, symbol.owner.startOffset, symbol.owner.endOffset)
}

internal fun IrPluginContext.irType(
  classId: ClassId,
  nullable: Boolean = false,
  arguments: List<IrTypeArgument> = emptyList(),
): IrType =
  finderForBuiltins()
    .findClass(classId)!!
    .createType(hasQuestionMark = nullable, arguments = arguments)

// Returns a type-compatible placeholder for constructor arguments that are ignored by default masks.
internal fun IrBuilderWithScope.defaultPrimitiveValue(
  type: IrType,
  pluginContext: IrPluginContext,
): IrExpression {
  if (type.isUnit()) return irGetObject(pluginContext.irBuiltIns.unitClass)
  if (type.isMarkedNullable() || type.isNothing() || type.isJavaLangVoid()) {
    return irNull(type.makeNullable())
  }

  return when (type.getPrimitiveType()) {
    PrimitiveType.BOOLEAN -> irBoolean(false)
    PrimitiveType.CHAR -> irChar(0.toChar())
    PrimitiveType.BYTE -> irByte(0)
    PrimitiveType.SHORT -> irShort(0)
    PrimitiveType.INT -> irInt(0)
    PrimitiveType.FLOAT -> 0.0f.toIrConst(type)
    PrimitiveType.LONG -> irLong(0L)
    PrimitiveType.DOUBLE -> 0.0.toIrConst(type)
    else -> irNull(type.makeNullable())
  }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrType.isJavaLangVoid(): Boolean {
  return classOrNull?.owner?.fqNameWhenAvailable == JAVA_LANG_VOID
}

internal val IrProperty.type: IrType
  get() =
    getter?.returnType
      ?: setter?.nonDispatchParameters?.first()?.type
      ?: backingField?.type
      ?: error("No type for property $name")

internal inline fun DescriptorVisibility.checkIsVisible(onError: (String) -> Nothing) {
  if (this != DescriptorVisibilities.PUBLIC && this != DescriptorVisibilities.INTERNAL) {
    onError("Visibility must be one of public or internal. Is $name")
  }
}

/** Returns the raw [IrClass] of this [IrType] or throws. */
internal fun IrType.rawType(): IrClass {
  return rawTypeOrNull() ?: error("Unrecognized type! $this")
}

/** Returns the raw [IrClass] of this [IrType] or null. */
@OptIn(UnsafeDuringIrConstructionAPI::class)
internal fun IrType.rawTypeOrNull(): IrClass? {
  return when (val classifier = classifierOrNull) {
    is IrClassSymbol -> classifier.owner
    else -> null
  }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal fun IrClass.addOverride(
  baseFqName: FqName,
  name: String,
  returnType: IrType,
  modality: Modality = Modality.FINAL,
  overloadFilter: (function: IrSimpleFunction) -> Boolean = { true },
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
          superClass.functions
            .filter { function ->
              function.name.asString() == name &&
                function.overridesFunctionIn(baseFqName) &&
                overloadFilter(function)
            }
            .map { it.symbol }
            .toList()
        }
  }

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal fun IrBuilderWithScope.irBinOp(
  pluginContext: IrPluginContext,
  name: Name,
  lhs: IrExpression,
  rhs: IrExpression,
): IrExpression {
  val classFqName = (lhs.type as IrSimpleType).classOrNull!!.owner.fqNameWhenAvailable!!
  val symbol =
    pluginContext
      .finderForBuiltins()
      .findFunctions(CallableId(ClassId.topLevel(classFqName), name))
      .single()
  return irInvoke(lhs, symbol, rhs)
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal fun IrBuilderWithScope.irInvoke(
  dispatchReceiver: IrExpression? = null,
  callee: IrFunctionSymbol,
  vararg args: IrExpression,
  typeHint: IrType? = null,
): IrMemberAccessExpression<*> {
  assert(callee.isBound) { "Symbol $callee expected to be bound" }
  val returnType = typeHint ?: callee.owner.returnType
  val call = irCall(callee, type = returnType)
  val dispatchReceiverIndex = callee.owner.dispatchReceiverParameter?.indexInParameters
  dispatchReceiver?.let { call.arguments[dispatchReceiverIndex!!] = dispatchReceiver }
  val parameterIndexOffset = if (dispatchReceiverIndex != null) 1 else 0
  args.forEachIndexed { index, valueArgument ->
    call.arguments[index + parameterIndexOffset] = valueArgument
  }
  return call
}

internal val IrProperty.isTransient: Boolean
  get() = backingField?.hasAnnotation(FqName("kotlin.jvm.Transient")) == true

internal fun IrBuilderWithScope.irAnd(
  pluginContext: IrPluginContext,
  lhs: IrExpression,
  rhs: IrExpression,
): IrExpression = irBinOp(pluginContext, OperatorNameConventions.AND, lhs, rhs)

internal fun Sequence<IrExpression>.joinToIrAnd(
  scope: IrBuilderWithScope,
  pluginContext: IrPluginContext,
): IrExpression {
  var compositeExpression: IrExpression? = null
  for (singleCheckExpr in this) {
    compositeExpression =
      if (compositeExpression == null) {
        singleCheckExpr
      } else {
        scope.irAnd(pluginContext, compositeExpression, singleCheckExpr)
      }
  }
  return compositeExpression ?: error("No expressions to join")
}

internal fun IrClass.generateToStringFun(
  pluginContext: IrPluginContext,
  targetName: String,
  generatedName: String = "GeneratedJsonAdapter",
): IrFunction {
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
              addArgument(irString("$generatedName("))
              addArgument(irString(targetName))
              addArgument(irString(")"))
            }
          )
        }
    }
}
