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

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.getSimpleFunction
import org.jetbrains.kotlin.name.FqName

internal class MoshiSealedSymbols(private val pluginContext: IrPluginContext) {
  val pjaf by lazy {
    pluginContext.referenceClass(
      FqName("com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory")
    )!!
  }
  val pjafOf by lazy { pjaf.getSimpleFunction("of")!! }
  val pjafWithSubtype by lazy { pjaf.getSimpleFunction("withSubtype")!! }
  val pjafWithDefaultValue by lazy { pjaf.getSimpleFunction("withDefaultValue")!! }
  val pjafWithFallbackJsonAdapter by lazy { pjaf.getSimpleFunction("withFallbackJsonAdapter")!! }

  val objectJsonAdapterCtor by lazy {
    pluginContext
      .referenceClass(FqName("dev.zacsweers.moshix.sealed.runtime.internal.ObjectJsonAdapter"))!!
      .constructors
      .single()
  }
}
