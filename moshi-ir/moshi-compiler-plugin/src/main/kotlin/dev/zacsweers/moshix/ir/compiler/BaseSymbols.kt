@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package dev.zacsweers.moshix.ir.compiler

import org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.createExtensionReceiver
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addGetter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.declarations.buildProperty
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrFactory
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrPackageFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.createEmptyExternalPackageFragment
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrPropertySymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.util.createThisReceiverParameter
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal open class BaseSymbols(
  protected val irBuiltIns: IrBuiltIns,
  protected val moduleFragment: IrModuleFragment,
  val pluginContext: IrPluginContext,
) {
  constructor(
    other: BaseSymbols
  ) : this(other.irBuiltIns, other.moduleFragment, other.pluginContext)

  protected val irFactory: IrFactory = pluginContext.irFactory
  private val javaLang: IrPackageFragment by lazy { createPackage("java.lang") }
  private val kotlinJvm: IrPackageFragment by lazy { createPackage("kotlin.jvm") }

  val emptySet by lazy {
    pluginContext
      .referenceFunctions(CallableId(FqName("kotlin.collections"), Name.identifier("emptySet")))
      .first()
  }

  /*
   * There are two setOf() functions with one arg - one is the vararg and the other is a shorthand for
   * Collections.singleton(element). It's important we pick the right one, otherwise we can accidentally send a
   * vararg array into the singleton() function.
   */

  val setOfVararg by lazy {
    pluginContext
      .referenceFunctions(CallableId(FqName("kotlin.collections"), Name.identifier("setOf")))
      .first {
        it.owner.nonDispatchParameters.size == 1 &&
          it.owner.nonDispatchParameters[0].varargElementType != null
      }
  }

  val setOfSingleton by lazy {
    pluginContext
      .referenceFunctions(CallableId(FqName("kotlin.collections"), Name.identifier("setOf")))
      .first {
        it.owner.nonDispatchParameters.size == 1 &&
          it.owner.nonDispatchParameters[0].varargElementType == null
      }
  }

  val setPlus by lazy {
    pluginContext
      .referenceFunctions(CallableId(FqName("kotlin.collections"), Name.identifier("plus")))
      .single {
        val owner = it.owner
        owner.nonDispatchParameters.firstOrNull()?.type?.classFqName ==
          FqName("kotlin.collections.Set") &&
          owner.nonDispatchParameters.size == 2 &&
          owner.nonDispatchParameters[1].type.classifierOrNull is IrTypeParameterSymbol
      }
  }

  val arrayGet by lazy {
    pluginContext.irBuiltIns.arrayClass.owner.declarations
      .filterIsInstance<IrSimpleFunction>()
      .single { it.name.asString() == "get" }
  }

  val arraySizeGetter by lazy {
    pluginContext.irBuiltIns.arrayClass.owner.declarations
      .filterIsInstance<IrProperty>()
      .single { it.name.asString() == "size" }
      .getter!!
  }

  val iterableJoinToString by lazy {
    pluginContext
      .referenceFunctions(CallableId(FqName("kotlin.collections"), Name.identifier("joinToString")))
      .single {
        it.owner.parameters
          .firstOrNull { it.kind == IrParameterKind.ExtensionReceiver }
          ?.type
          ?.classFqName == FqName("kotlin.collections.Iterable")
      }
  }

  protected val javaLangClass: IrClassSymbol by lazy {
    createClass(javaLang, "Class", ClassKind.CLASS, Modality.FINAL)
  }

  @OptIn(DeprecatedForRemovalCompilerApi::class)
  protected val kotlinKClassJava: IrPropertySymbol =
    irFactory
      .buildProperty { name = Name.identifier("java") }
      .apply {
        parent = kotlinJvm
        addGetter().apply {
          extensionReceiverParameter =
            createExtensionReceiver(irBuiltIns.kClassClass.starProjectedType)
          returnType = javaLangClass.defaultType
        }
      }
      .symbol

  @OptIn(DeprecatedForRemovalCompilerApi::class)
  protected val kotlinKClassJavaObjectType: IrPropertySymbol =
    irFactory
      .buildProperty { name = Name.identifier("javaObjectType") }
      .apply {
        parent = kotlinJvm
        addGetter().apply {
          extensionReceiverParameter =
            createExtensionReceiver(irBuiltIns.kClassClass.starProjectedType)
          returnType = javaLangClass.defaultType
        }
      }
      .symbol

  protected fun createPackage(packageName: String): IrPackageFragment =
    createEmptyExternalPackageFragment(moduleFragment.descriptor, FqName(packageName))

  protected fun createClass(
    irParent: IrDeclarationParent,
    shortName: String,
    classKind: ClassKind,
    classModality: Modality,
    isValueClass: Boolean = false,
    body: IrClass.() -> Unit = {},
  ): IrClassSymbol =
    irFactory
      .buildClass {
        name = Name.identifier(shortName)
        kind = classKind
        modality = classModality
        isValue = isValueClass
      }
      .apply {
        parent = irParent
        createThisReceiverParameter()
        body()
      }
      .symbol

  private fun IrBuilderWithScope.kClassReference(classType: IrType) =
    IrClassReferenceImpl(
      startOffset,
      endOffset,
      context.irBuiltIns.kClassClass.starProjectedType,
      context.irBuiltIns.kClassClass,
      classType,
    )

  private fun IrBuilderWithScope.kClassToJavaClass(kClassReference: IrExpression) =
    irGet(javaLangClass.starProjectedType, kClassReference, kotlinKClassJava.owner.getter!!.symbol)

  private fun IrBuilderWithScope.kClassToJavaObjectClass(kClassReference: IrExpression) =
    irGet(
      javaLangClass.starProjectedType,
      kClassReference,
      kotlinKClassJavaObjectType.owner.getter!!.symbol,
    )

  // Produce a static reference to the java class of the given type.
  fun javaClassReference(
    irBuilder: IrBuilderWithScope,
    classType: IrType,
    forceObjectType: Boolean = false,
  ) =
    with(irBuilder) {
      val kClassReference = kClassReference(classType)
      if (forceObjectType) {
        kClassToJavaObjectClass(kClassReference)
      } else {
        kClassToJavaClass(kClassReference)
      }
    }
}
