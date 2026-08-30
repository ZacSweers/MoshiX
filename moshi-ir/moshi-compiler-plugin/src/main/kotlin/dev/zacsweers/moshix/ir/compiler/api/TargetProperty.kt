// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.ir.compiler.api

import dev.zacsweers.moshix.ir.compiler.util.type
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.TypeRemapper

/** A property in user code that maps to JSON. */
internal data class TargetProperty(
  val property: IrProperty,
  val parameter: TargetParameter?,
  val jsonName: String?,
  val jsonIgnore: Boolean,
  val type: IrType = parameter?.type ?: property.type,
) {
  val name: String
    get() = property.name.identifier

  val parameterIndex: Int
    get() = parameter?.index ?: -1

  val hasDefault: Boolean
    get() = parameter?.hasDefault ?: true

  internal fun remapTypes(remapper: TypeRemapper): TargetProperty {
    val remappedParameter = parameter?.remapTypes(remapper)
    return copy(parameter = remappedParameter, type = remapper.remapType(type))
  }

  override fun toString(): String = name
}
