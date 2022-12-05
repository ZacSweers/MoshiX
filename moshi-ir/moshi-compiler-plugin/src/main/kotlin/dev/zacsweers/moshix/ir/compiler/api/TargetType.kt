/*
 * Copyright (C) 2018 Zac Sweers
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
package dev.zacsweers.moshix.ir.compiler.api

import dev.zacsweers.moshix.ir.compiler.util.checkIsVisible
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.types.IrType

/** A user type that should be decoded and encoded by generated code. */
internal data class TargetType(
  val irClass: IrClass,
  val irType: IrType,
  val constructor: TargetConstructor,
  val properties: Map<String, TargetProperty>,
  val typeVariables: List<IrTypeParameter>,
  val isDataClass: Boolean,
  val visibility: DescriptorVisibility,
) {

  init {
    visibility.checkIsVisible()
  }
}
