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
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.impl.IrExternalPackageFragmentImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeArgument
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.createType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal class MoshiSymbols(
    private val irBuiltIns: IrBuiltIns,
    private val moduleFragment: IrModuleFragment,
    private val pluginContext: IrPluginContext
) {
  private val irFactory: IrFactory = pluginContext.irFactory

  private val moshiPackage: IrPackageFragment by lazy { createPackage("com.squareup.moshi") }
  private val javaReflectPackage: IrPackageFragment by lazy { createPackage("java.lang.reflect") }

  val type: IrClassSymbol by lazy {
    createClass(javaReflectPackage, "Type", ClassKind.INTERFACE, Modality.ABSTRACT)
  }

  val jsonReader: IrClassSymbol by lazy {
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
  }

  val jsonWriter: IrClassSymbol by lazy {
    irFactory
        .buildClass {
          name = Name.identifier("JsonWriter")
          kind = ClassKind.CLASS
          modality = Modality.ABSTRACT
        }
        .apply {
          createImplicitParameterDeclarationWithWrappedDescriptor()
          parent = moshiPackage

          addFunction(Name.identifier("beginObject").identifier, defaultType, Modality.ABSTRACT)
          addFunction(Name.identifier("endObject").identifier, defaultType, Modality.ABSTRACT)

          addFunction(Name.identifier("name").identifier, defaultType, Modality.ABSTRACT).apply {
            addValueParameter("name", irBuiltIns.stringType)
          }
        }
        .symbol
  }

  val moshi: IrClassSymbol by lazy {
    irFactory
        .buildClass {
          name = Name.identifier("Moshi")
          kind = ClassKind.CLASS
          modality = Modality.FINAL
        }
        .apply {
          createImplicitParameterDeclarationWithWrappedDescriptor()
          parent = moshiPackage

          addFunction(
              Name.identifier("adapter").identifier,
              jsonAdapter.typeWith(irBuiltIns.anyNType),
              Modality.FINAL)
              .apply {
                addTypeParameter("T", irBuiltIns.anyNType)
                addValueParameter("type", type.defaultType)
                addValueParameter(
                    "annotations", irBuiltIns.setClass.typeWith(irBuiltIns.annotationType))
                addValueParameter("fieldName", irBuiltIns.stringType)
              }
        }
        .symbol
  }

  internal val jsonAdapter: IrClassSymbol by lazy {
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

          addFunction(Name.identifier("toJson").identifier, irBuiltIns.unitType, Modality.ABSTRACT)
              .apply {
                addValueParameter("writer", jsonWriter.defaultType)
                addValueParameter("value", t.defaultType.makeNullable())
              }
        }
        .symbol
  }

  val jsonDataException: IrClassSymbol by lazy {
    pluginContext.referenceClass(FqName("com.squareup.moshi.JsonDataException"))!!
  }

  val jsonDataExceptionStringConstructor: IrFunctionSymbol by lazy {
    jsonDataException.constructors.first {
      it.owner.valueParameters.size == 1 &&
          it.owner.valueParameters[0].type.makeNotNull() == pluginContext.irBuiltIns.stringType
    }
  }

  // TODO why doesn't creating an anonymous irclass work here? Breaks bytecode somewhere
  val moshiUtil: IrClassSymbol by lazy {
    pluginContext.referenceClass(FqName("com.squareup.moshi.internal.Util"))!!
  }

  val emptySet by lazy {
    pluginContext.referenceFunctions(FqName("kotlin.collections.emptySet")).first()
  }

  /*
   * There are two setOf() functions with one arg - one is the vararg and the other is a shorthand for
   * Collections.singleton(element). It's important we pick the right one, otherwise we can accidentally send a
   * vararg array into the singleton() function.
   */

  val setOfVararg by lazy {
    pluginContext.referenceFunctions(FqName("kotlin.collections.setOf")).first {
      it.owner.valueParameters.size == 1 || it.owner.valueParameters[0].varargElementType != null
    }
  }

  val setOfSingleton by lazy {
    pluginContext.referenceFunctions(FqName("kotlin.collections.setOf")).first {
      it.owner.valueParameters.size == 1 || it.owner.valueParameters[0].varargElementType == null
    }
  }

  val setPlus by lazy {
    pluginContext.referenceFunctions(FqName("kotlin.collections.plus")).single {
      val owner = it.owner
      owner.extensionReceiverParameter?.type?.classFqName == FqName("kotlin.collections.Set") &&
          owner.valueParameters.size == 1 &&
          owner.valueParameters[0].type.classifierOrNull is IrTypeParameterSymbol
    }
  }

  val arrayGet by lazy {
    pluginContext.irBuiltIns.arrayClass.owner.declarations.filterIsInstance<IrSimpleFunction>()
        .single { it.name.asString() == "get" }
  }

  val arraySizeGetter by lazy {
    pluginContext
        .irBuiltIns
        .arrayClass
        .owner
        .declarations
        .filterIsInstance<IrProperty>()
        .single { it.name.asString() == "size" }
        .getter!!
  }

  val iterableJoinToString by lazy {
    pluginContext.referenceFunctions(FqName("kotlin.collections.joinToString")).single {
      it.owner.extensionReceiverParameter?.type?.classFqName ==
          FqName("kotlin.collections.Iterable")
    }
  }

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
}
