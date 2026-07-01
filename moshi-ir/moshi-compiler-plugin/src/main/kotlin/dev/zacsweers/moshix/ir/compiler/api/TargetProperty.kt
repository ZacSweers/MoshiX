// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.ir.compiler.api

import dev.zacsweers.moshix.ir.compiler.util.type
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.types.IrType

/** A property in user code that maps to JSON. */
internal data class TargetProperty(
  val property: IrProperty,
  val parameter: TargetParameter?,
  val jsonName: String?,
  val jsonIgnore: Boolean,
) {
  val name: String
    get() = property.name.identifier

  val type: IrType
    get() = parameter?.type ?: property.type

  val parameterIndex: Int
    get() = parameter?.index ?: -1

  val hasDefault: Boolean
    get() = parameter?.hasDefault ?: true

  override fun toString(): String = name
}
