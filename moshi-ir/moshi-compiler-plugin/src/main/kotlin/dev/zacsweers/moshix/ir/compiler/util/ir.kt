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
package dev.zacsweers.moshix.ir.compiler.util

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.jvm.ir.erasedUpperBound
import org.jetbrains.kotlin.builtins.PrimitiveType
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocationWithRange
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.codegen.CompilationException
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.IrGeneratorContext
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irChar
import org.jetbrains.kotlin.ir.builders.irConcat
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irLong
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrInstanceInitializerCall
import org.jetbrains.kotlin.ir.expressions.IrMemberAccessExpression
import org.jetbrains.kotlin.ir.expressions.addArgument
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeArgument
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.createType
import org.jetbrains.kotlin.ir.types.getPrimitiveType
import org.jetbrains.kotlin.ir.types.isMarkedNullable
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.allOverridden
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.util.OperatorNameConventions

/*
 * Adapted from Zipline: https://github.com/cashapp/zipline/blob/06f9f5d735/zipline-kotlin-plugin-hosted/src/main/kotlin/app/cash/zipline/kotlin/ir.kt
 */

/** Finds the line and column of [this] within its file. */
internal fun IrDeclaration.location(): CompilerMessageSourceLocation {
  return locationIn(file)
}

/** Finds the line and column of [this] within this [file]. */
internal fun IrElement?.locationIn(file: IrFile): CompilerMessageSourceLocation {
  val sourceRangeInfo =
    file.fileEntry.getSourceRangeInfo(
      beginOffset = this?.startOffset ?: SYNTHETIC_OFFSET,
      endOffset = this?.endOffset ?: SYNTHETIC_OFFSET
    )
  return CompilerMessageLocationWithRange.create(
    path = sourceRangeInfo.filePath,
    lineStart = sourceRangeInfo.startLineNumber + 1,
    columnStart = sourceRangeInfo.startColumnNumber + 1,
    lineEnd = sourceRangeInfo.endLineNumber + 1,
    columnEnd = sourceRangeInfo.endColumnNumber + 1,
    lineContent = null
  )!!
}

internal inline fun MessageCollector.error(message: () -> String) = error(null, message)

internal inline fun MessageCollector.error(declaration: IrDeclaration, message: () -> String) =
  error(declaration::location, message)

internal inline fun MessageCollector.error(
  noinline location: (() -> CompilerMessageSourceLocation)?,
  message: () -> String
) {
  report(CompilerMessageSeverity.ERROR, message(), location?.invoke())
}

internal fun IrConstructor.irConstructorBody(
  context: IrGeneratorContext,
  blockBody: DeclarationIrBuilder.(MutableList<IrStatement>) -> Unit
) {
  val startOffset = UNDEFINED_OFFSET
  val endOffset = UNDEFINED_OFFSET
  val constructorIrBuilder =
    DeclarationIrBuilder(
      generatorContext = context,
      symbol = IrSimpleFunctionSymbolImpl(),
      startOffset = startOffset,
      endOffset = endOffset
    )
  body =
    context.irFactory.createBlockBody(
      startOffset = startOffset,
      endOffset = endOffset,
    ) {
      constructorIrBuilder.blockBody(statements)
    }
}

internal fun DeclarationIrBuilder.irInstanceInitializerCall(
  context: IrGeneratorContext,
  classSymbol: IrClassSymbol,
): IrInstanceInitializerCall {
  return IrInstanceInitializerCallImpl(
    startOffset = startOffset,
    endOffset = endOffset,
    classSymbol = classSymbol,
    type = context.irBuiltIns.unitType,
  )
}

internal fun IrClass.isSubclassOfFqName(fqName: String): Boolean =
  fqNameWhenAvailable?.asString() == fqName ||
    superTypes.any { it.erasedUpperBound.isSubclassOfFqName(fqName) }

internal fun IrSimpleFunction.overridesFunctionIn(fqName: FqName): Boolean =
  parentClassOrNull?.fqNameWhenAvailable == fqName ||
    allOverridden().any { it.parentClassOrNull?.fqNameWhenAvailable == fqName }

internal fun IrPluginContext.createIrBuilder(symbol: IrSymbol): DeclarationIrBuilder {
  return DeclarationIrBuilder(this, symbol, symbol.owner.startOffset, symbol.owner.endOffset)
}

internal fun IrPluginContext.irType(
  classId: ClassId,
  nullable: Boolean = false,
  arguments: List<IrTypeArgument> = emptyList()
): IrType = referenceClass(classId)!!.createType(hasQuestionMark = nullable, arguments = arguments)

// returns null: Any? for boxed types and 0: <number type> for primitives
internal fun IrBuilderWithScope.defaultPrimitiveValue(
  type: IrType,
  pluginContext: IrPluginContext
): IrExpression {
  // TODO check unit/void/nothing
  val defaultPrimitive: IrExpression? =
    if (type.isMarkedNullable()) {
      null
    } else {
      when (type.getPrimitiveType()) {
        PrimitiveType.BOOLEAN -> irBoolean(false)
        PrimitiveType.CHAR -> irChar(0.toChar())
        PrimitiveType.BYTE -> IrConstImpl.byte(startOffset, endOffset, type, 0)
        PrimitiveType.SHORT -> IrConstImpl.short(startOffset, endOffset, type, 0)
        PrimitiveType.INT -> irInt(0)
        PrimitiveType.FLOAT -> IrConstImpl.float(startOffset, endOffset, type, 0.0f)
        PrimitiveType.LONG -> irLong(0L)
        PrimitiveType.DOUBLE -> IrConstImpl.double(startOffset, endOffset, type, 0.0)
        else -> null
      }
    }
  return defaultPrimitive ?: irNull(pluginContext.irBuiltIns.anyNType)
}

internal val IrProperty.type: IrType
  get() =
    getter?.returnType
      ?: setter?.valueParameters?.first()?.type ?: backingField?.type
        ?: error("No type for property $name")

internal fun DescriptorVisibility.checkIsVisible() {
  if (this != DescriptorVisibilities.PUBLIC && this != DescriptorVisibilities.INTERNAL) {
    throw CompilationException("Visibility must be one of public or internal. Is $name", null, null)
  }
}

/** Returns the raw [IrClass] of this [IrType] or throws. */
internal fun IrType.rawType(): IrClass {
  return rawTypeOrNull() ?: error("Unrecognized type! $this")
}

/** Returns the raw [IrClass] of this [IrType] or null. */
internal fun IrType.rawTypeOrNull(): IrClass? {
  return when (val classifier = classifierOrNull) {
    is IrClassSymbol -> classifier.owner
    else -> null
  }
}

internal fun IrClass.addOverride(
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

internal fun IrBuilderWithScope.irBinOp(
  pluginContext: IrPluginContext,
  name: Name,
  lhs: IrExpression,
  rhs: IrExpression
): IrExpression {
  val classFqName = (lhs.type as IrSimpleType).classOrNull!!.owner.fqNameWhenAvailable!!
  val symbol =
    pluginContext.referenceFunctions(CallableId(ClassId.topLevel(classFqName), name)).single()
  return irInvoke(lhs, symbol, rhs)
}

internal fun IrBuilderWithScope.irInvoke(
  dispatchReceiver: IrExpression? = null,
  callee: IrFunctionSymbol,
  vararg args: IrExpression,
  typeHint: IrType? = null
): IrMemberAccessExpression<*> {
  assert(callee.isBound) { "Symbol $callee expected to be bound" }
  val returnType = typeHint ?: callee.owner.returnType
  val call = irCall(callee, type = returnType)
  call.dispatchReceiver = dispatchReceiver
  args.forEachIndexed(call::putValueArgument)
  return call
}

internal val IrProperty.isTransient: Boolean
  get() = backingField?.hasAnnotation(FqName("kotlin.jvm.Transient")) == true

internal fun IrBuilderWithScope.irAnd(
  pluginContext: IrPluginContext,
  lhs: IrExpression,
  rhs: IrExpression
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
