/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Copyright (C) 2026 Zac Sweers
 * SPDX-License-Identifier: Apache-2.0
 *
 * Use of this source code is governed by the Apache 2.0 license in LICENSE.txt.
 *
 * Adapted from Kotlin 2.4.20's IrUtils.kt, with a local package and List.map:
 * https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/util/IrUtils.kt#L718-L750
 * Kotlin 2.5 removed this helper, so MoshiX keeps its own copy.
 */
package dev.zacsweers.moshix.ir.compiler.util

import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.createBlockBody
import org.jetbrains.kotlin.ir.expressions.impl.IrDelegatingConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.util.copyTo

@Suppress("KotlincFE10") // IR constructor visibility still uses DescriptorVisibility.
internal fun IrClass.addSimpleDelegatingConstructor(
  superConstructor: IrConstructor,
  irBuiltIns: IrBuiltIns,
  isPrimary: Boolean = false,
  origin: IrDeclarationOrigin? = null,
): IrConstructor = addConstructor {
  val klass = this@addSimpleDelegatingConstructor
  this.startOffset = klass.startOffset
  this.endOffset = klass.endOffset
  this.origin = origin ?: klass.origin
  this.visibility = superConstructor.visibility
  this.isPrimary = isPrimary
}
  .also { constructor ->
    constructor.parameters =
      superConstructor.parameters.map { parameter ->
        parameter.copyTo(constructor)
      }

    constructor.body =
      factory.createBlockBody(
        startOffset,
        endOffset,
        listOf(
          IrDelegatingConstructorCallImpl(
              startOffset,
              endOffset,
              irBuiltIns.unitType,
              superConstructor.symbol,
              0,
            )
            .apply {
              constructor.parameters.forEach { parameter ->
                arguments[parameter.indexInParameters] =
                  IrGetValueImpl(startOffset, endOffset, parameter.type, parameter.symbol)
              }
            },
          IrInstanceInitializerCallImpl(startOffset, endOffset, this.symbol, irBuiltIns.unitType),
        ),
      )
  }
