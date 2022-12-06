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
package dev.zacsweers.moshix.ir.compiler.sealed

import dev.zacsweers.moshix.ir.compiler.BaseSymbols
import dev.zacsweers.moshix.ir.compiler.MoshiSymbols
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addTypeParameter
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.declarations.IrPackageFragment
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.types.typeWithParameters
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.getSimpleFunction
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.Variance

internal class MoshiSealedSymbols(
  private val moshiSymbols: MoshiSymbols,
) : BaseSymbols(moshiSymbols) {

  private val moshiAdaptersPackage: IrPackageFragment by lazy {
    createPackage("com.squareup.moshi.adapters")
  }

  val pjaf by lazy {
    // TODO https://youtrack.jetbrains.com/issue/KT-55269
    //    pluginContext.referenceClass(
    //      ClassId.fromString("com/squareup/moshi/adapters/PolymorphicJsonAdapterFactory")
    //    )!!
    createClass(
      moshiAdaptersPackage,
      "PolymorphicJsonAdapterFactory",
      ClassKind.CLASS,
      Modality.FINAL
    ) {
      val classTypeParameter = addTypeParameter("T", irBuiltIns.anyNType, Variance.INVARIANT)
      //  val pjafWithDefaultValue by lazy { pjaf.getSimpleFunction("withDefaultValue")!! }
      //  val pjafWithFallbackJsonAdapter by lazy {
      // pjaf.getSimpleFunction("withFallbackJsonAdapter")!! }
      // public static <T> PolymorphicJsonAdapterFactory<T> of(Class<T> baseType, String labelKey)
      addFunction(
          name = Name.identifier("of").identifier,
          returnType = defaultType,
          isStatic = true,
        )
        .apply {
          val typeParameter = addTypeParameter("T", irBuiltIns.anyNType, Variance.INVARIANT)
          addValueParameter("baseType", javaLangClass.typeWithParameters(listOf(typeParameter)))
          addValueParameter("labelKey", irBuiltIns.stringType)
        }

      // public PolymorphicJsonAdapterFactory<T> withSubtype(Class<? extends T> subtype, String
      // label) {
      addFunction(
          name = Name.identifier("withSubtype").identifier,
          returnType = typeWith(classTypeParameter.defaultType),
        )
        .apply {
          // TODO need ? extends T
          addValueParameter("subtype", javaLangClass.typeWithParameters(listOf(classTypeParameter)))
          addValueParameter("label", irBuiltIns.stringType)
        }

      // public PolymorphicJsonAdapterFactory<T> withDefaultValue(@Nullable T defaultValue)
      addFunction(
          name = Name.identifier("withDefaultValue").identifier,
          returnType = typeWith(classTypeParameter.defaultType),
        )
        .apply { addValueParameter("defaultValue", classTypeParameter.defaultType.makeNullable()) }

      // public PolymorphicJsonAdapterFactory<T> withFallbackJsonAdapter(@Nullable
      // JsonAdapter<Object> fallbackJsonAdapter)
      addFunction(
          name = Name.identifier("withFallbackJsonAdapter").identifier,
          returnType = typeWith(classTypeParameter.defaultType),
        )
        .apply {
          addValueParameter(
            "fallbackJsonAdapter",
            moshiSymbols.jsonAdapter.typeWith(irBuiltIns.anyNType).makeNullable()
          )
        }
    }
  }
  val pjafOf by lazy { pjaf.getSimpleFunction("of")!! }
  val pjafWithSubtype by lazy { pjaf.getSimpleFunction("withSubtype")!! }
  val pjafWithDefaultValue by lazy { pjaf.getSimpleFunction("withDefaultValue")!! }
  val pjafWithFallbackJsonAdapter by lazy { pjaf.getSimpleFunction("withFallbackJsonAdapter")!! }

  val objectJsonAdapterCtor by lazy {
    pluginContext
      .referenceClass(
        ClassId.fromString("dev/zacsweers/moshix/sealed/runtime/internal/ObjectJsonAdapter")
      )!!
      .constructors
      .single()
  }
}
