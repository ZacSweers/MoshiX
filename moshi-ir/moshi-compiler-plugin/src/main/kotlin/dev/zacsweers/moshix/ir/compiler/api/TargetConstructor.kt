// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.ir.compiler.api

import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.ir.declarations.IrConstructor

/** A constructor in user code that should be called by generated code. */
internal data class TargetConstructor(
  val irConstructor: IrConstructor,
  val parameters: LinkedHashMap<String, TargetParameter>,
  val visibility: DescriptorVisibility,
)
