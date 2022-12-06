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
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addTypeParameter
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrPackageFragment
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.createImplicitParameterDeclarationWithWrappedDescriptor
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getSimpleFunction
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal class MoshiSymbols(
  irBuiltIns: IrBuiltIns,
  moduleFragment: IrModuleFragment,
  pluginContext: IrPluginContext
) : BaseSymbols(irBuiltIns, moduleFragment, pluginContext) {
  private val moshiPackage: IrPackageFragment by lazy { createPackage("com.squareup.moshi") }
  private val moshiInternalPackage: IrPackageFragment by lazy {
    createPackage("com.squareup.moshi.internal")
  }
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
          Name.identifier("beginObject").identifier,
          irBuiltIns.unitType,
          Modality.ABSTRACT
        )
        addFunction(Name.identifier("endObject").identifier, irBuiltIns.unitType, Modality.ABSTRACT)
        addFunction(Name.identifier("skipName").identifier, irBuiltIns.unitType, Modality.ABSTRACT)
        addFunction(Name.identifier("skipValue").identifier, irBuiltIns.unitType, Modality.ABSTRACT)

        addFunction(
          Name.identifier("hasNext").identifier,
          irBuiltIns.booleanType,
          Modality.ABSTRACT
        )

        val optionsClass = createClass(this, "Options", ClassKind.CLASS, Modality.FINAL)

        addFunction(Name.identifier("selectName").identifier, irBuiltIns.intType, Modality.ABSTRACT)
          .apply { addValueParameter("options", optionsClass.defaultType) }
      }
      .symbol
  }

  val jsonReaderOptionsOf by lazy {
    // TODO https://youtrack.jetbrains.com/issue/KT-55269
    //    pluginContext
    //      .referenceClass(ClassId.fromString("com/squareup/moshi/JsonReader.Options"))!!
    //      .functions
    //      .single { it.owner.name.asString() == "of" }
    createClass(jsonReader.owner, "Options", ClassKind.CLASS, Modality.FINAL) {
        // public static Options of(String... strings)
        addFunction(
            name = Name.identifier("of").identifier,
            returnType = defaultType,
            isStatic = true,
          )
          .apply {
            addValueParameter {
              name = Name.identifier("strings")
              type = irBuiltIns.arrayClass.typeWith(irBuiltIns.stringType)
              varargElementType = irBuiltIns.stringType
            }
          }
      }
      .functions
      .single()
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

  val moshiBuilder by lazy {
    pluginContext.referenceClass(ClassId.fromString("com/squareup/moshi/Moshi.Builder"))!!
  }

  val moshiBuilderBuild by lazy { moshiBuilder.getSimpleFunction("build")!! }

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
            Modality.FINAL
          )
          .apply {
            addTypeParameter("T", irBuiltIns.anyNType)
            addValueParameter("type", type.defaultType)
            addValueParameter(
              "annotations",
              irBuiltIns.setClass.typeWith(irBuiltIns.annotationType)
            )
            addValueParameter("fieldName", irBuiltIns.stringType)
          }

        addFunction(
          Name.identifier("newBuilder").identifier,
          moshiBuilder.defaultType,
          Modality.FINAL
        )
      }
      .symbol
  }

  val moshiThreeArgAdapter by lazy { moshi.getSimpleFunction("adapter")!! }

  val moshiNewBuilder by lazy { moshi.getSimpleFunction("newBuilder")!! }

  val jsonAdapterFactoryCreate by lazy {
    pluginContext
      .referenceClass(ClassId.fromString("com/squareup/moshi/JsonAdapter.Factory"))!!
      .getSimpleFunction("create")!!
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
            Modality.ABSTRACT
          )
          .apply { addValueParameter("reader", jsonReader.defaultType) }

        addFunction(Name.identifier("toJson").identifier, irBuiltIns.unitType, Modality.ABSTRACT)
          .apply {
            addValueParameter("writer", jsonWriter.defaultType)
            addValueParameter("value", t.defaultType.makeNullable())
          }
      }
      .symbol
  }

  val addAdapter by lazy {
    pluginContext
      .referenceFunctions(CallableId(FqName("com.squareup.moshi"), Name.identifier("addAdapter")))
      .first()
  }

  val jsonDataException: IrClassSymbol by lazy {
    pluginContext.referenceClass(ClassId.fromString("com/squareup/moshi/JsonDataException"))!!
  }

  val jsonDataExceptionStringConstructor: IrFunctionSymbol by lazy {
    jsonDataException.constructors.first {
      it.owner.valueParameters.size == 1 &&
        it.owner.valueParameters[0].type.makeNotNull() == pluginContext.irBuiltIns.stringType
    }
  }

  val moshiUtil: IrClassSymbol by lazy {
    // TODO https://youtrack.jetbrains.com/issue/KT-55269
    //    pluginContext.referenceClass(ClassId.fromString("com/squareup/moshi/internal/Util"))!!
    createClass(moshiInternalPackage, "Util", ClassKind.CLASS, Modality.FINAL) {
      // public static JsonDataException missingProperty(String propertyName, String jsonName,
      // JsonReader reader)
      addFunction(
          name = Name.identifier("missingProperty").identifier,
          returnType = jsonDataException.defaultType,
          isStatic = true,
        )
        .apply {
          addValueParameter("propertyName", irBuiltIns.stringType)
          addValueParameter("jsonName", irBuiltIns.stringType)
          addValueParameter("reader", jsonReader.defaultType)
        }

      // public static JsonDataException unexpectedNull(String propertyName, String jsonName,
      // JsonReader reader)
      addFunction(
          name = Name.identifier("unexpectedNull").identifier,
          returnType = jsonDataException.defaultType,
          isStatic = true,
        )
        .apply {
          addValueParameter("propertyName", irBuiltIns.stringType)
          addValueParameter("jsonName", irBuiltIns.stringType)
          addValueParameter("reader", jsonReader.defaultType)
        }
    }
  }

  val moshiTypes by lazy {
    pluginContext.referenceClass(ClassId.fromString("com/squareup/moshi/Types"))!!
  }

  val moshiTypesArrayOf by lazy { moshiTypes.getSimpleFunction("arrayOf")!! }

  val moshiTypesNewParameterizedTypeWithOwner by lazy {
    moshiTypes.getSimpleFunction("newParameterizedTypeWithOwner")!!
  }

  val moshiTypesNewParameterizedType by lazy {
    moshiTypes.getSimpleFunction("newParameterizedType")!!
  }

  val moshiTypesSubtypeOf by lazy { moshiTypes.getSimpleFunction("subtypeOf")!! }

  val moshiTypesSuperTypeOf by lazy { moshiTypes.getSimpleFunction("supertypeOf")!! }
}
