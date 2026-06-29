// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.ir.compiler.api

import org.jetbrains.kotlin.ir.declarations.IrClass

internal interface AdapterGenerator {
  fun prepare(): PreparedAdapter?
}

/** Represents a prepared adapter with its [adapterClass]. */
internal data class PreparedAdapter(val adapterClass: IrClass)
