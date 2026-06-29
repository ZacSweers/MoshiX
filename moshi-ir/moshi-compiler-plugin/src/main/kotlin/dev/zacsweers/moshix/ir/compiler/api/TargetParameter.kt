// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.ir.compiler.api

import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.types.IrType

/** A parameter in user code that should be populated by generated code. */
internal data class TargetParameter(
  val name: String,
  val index: Int,
  val type: IrType,
  val hasDefault: Boolean,
  val jsonName: String? = null,
  val jsonIgnore: Boolean = false,
  val qualifiers: Set<IrConstructorCall>? = null,
)
