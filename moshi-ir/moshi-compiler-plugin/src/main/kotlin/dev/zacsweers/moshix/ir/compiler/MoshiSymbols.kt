package dev.zacsweers.moshix.ir.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.createImplicitParameterDeclarationWithWrappedDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addTypeParameter
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrFactory
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrPackageFragment
import org.jetbrains.kotlin.ir.declarations.impl.IrExternalPackageFragmentImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeArgument
import org.jetbrains.kotlin.ir.types.createType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal class MoshiSymbols(
    val irBuiltIns: IrBuiltIns,
    private val moduleFragment: IrModuleFragment,
    private val pluginContext: IrPluginContext
) {
  private val irFactory: IrFactory = pluginContext.irFactory

  private val moshiPackage: IrPackageFragment = createPackage("com.squareup.moshi")

  val jsonReader: IrClassSymbol =
      irFactory
          .buildClass {
            name = Name.identifier("JsonReader")
            kind = ClassKind.CLASS
            modality = Modality.ABSTRACT
          }
          .apply {
            createImplicitParameterDeclarationWithWrappedDescriptor()
            parent = moshiPackage

            addFunction(
                Name.identifier("beginObject").identifier, irBuiltIns.unitType, Modality.ABSTRACT)
            addFunction(
                Name.identifier("endObject").identifier, irBuiltIns.unitType, Modality.ABSTRACT)
            addFunction(
                Name.identifier("skipName").identifier, irBuiltIns.unitType, Modality.ABSTRACT)
            addFunction(
                Name.identifier("skipValue").identifier, irBuiltIns.unitType, Modality.ABSTRACT)

            addFunction(
                Name.identifier("hasNext").identifier, irBuiltIns.booleanType, Modality.ABSTRACT)

            val optionsClass = createClass(this, "Options", ClassKind.CLASS, Modality.FINAL)

            addFunction(
                Name.identifier("selectName").identifier, irBuiltIns.intType, Modality.ABSTRACT)
                .apply { addValueParameter("options", optionsClass.defaultType) }
          }
          .symbol

  val jsonWriter: IrClassSymbol =
      irFactory
          .buildClass {
            name = Name.identifier("JsonWriter")
            kind = ClassKind.CLASS
            modality = Modality.ABSTRACT
          }
          .apply {
            createImplicitParameterDeclarationWithWrappedDescriptor()
            parent = moshiPackage

            addFunction(
                Name.identifier("beginObject").identifier, defaultType, Modality.ABSTRACT)
            addFunction(
                Name.identifier("endObject").identifier, defaultType, Modality.ABSTRACT)

            addFunction(
                Name.identifier("name").identifier, defaultType, Modality.ABSTRACT).apply {
              addValueParameter("name", irBuiltIns.stringType)
            }
          }.symbol

  internal val jsonAdapter: IrClassSymbol =
      irFactory
          .buildClass {
            name = Name.identifier("JsonAdapter")
            kind = ClassKind.CLASS
            modality = Modality.ABSTRACT
          }
          .apply {
            createImplicitParameterDeclarationWithWrappedDescriptor()
            val t = addTypeParameter("T", irBuiltIns.anyNType)
            parent = moshiPackage

            addConstructor {}

            addFunction(
                Name.identifier("fromJson").identifier,
                t.defaultType.makeNullable(),
                Modality.ABSTRACT)
                .apply { addValueParameter("reader", jsonReader.defaultType) }

            addFunction(
                Name.identifier("toJson").identifier, irBuiltIns.unitType, Modality.ABSTRACT)
                .apply {
                  addValueParameter("writer", jsonWriter.defaultType)
                  addValueParameter("value", t.defaultType.makeNullable())
                }
          }
          .symbol

  private fun createPackage(packageName: String): IrPackageFragment =
      IrExternalPackageFragmentImpl.createEmptyExternalPackageFragment(
          moduleFragment.descriptor, FqName(packageName))

  private fun createClass(
      irParent: IrDeclarationParent,
      shortName: String,
      classKind: ClassKind,
      classModality: Modality,
      isInlineClass: Boolean = false
  ): IrClassSymbol =
      irFactory
          .buildClass {
            name = Name.identifier(shortName)
            kind = classKind
            modality = classModality
            isInline = isInlineClass
          }
          .apply {
            parent = irParent
            createImplicitParameterDeclarationWithWrappedDescriptor()
          }
          .symbol

  internal fun irType(
      qualifiedName: String,
      nullable: Boolean = false,
      arguments: List<IrTypeArgument> = emptyList()
  ): IrType =
      pluginContext.referenceClass(FqName(qualifiedName))!!.createType(
          hasQuestionMark = nullable, arguments = arguments)

  //  fun createBuilder(
  //    symbol: IrSymbol,
  //    startOffset: Int = UNDEFINED_OFFSET,
  //    endOffset: Int = UNDEFINED_OFFSET
  //  ) = AndroidIrBuilder(this, symbol, startOffset, endOffset)
}
