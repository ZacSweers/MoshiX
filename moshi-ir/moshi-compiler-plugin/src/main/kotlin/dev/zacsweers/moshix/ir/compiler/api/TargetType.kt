// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.ir.compiler.api

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
)
