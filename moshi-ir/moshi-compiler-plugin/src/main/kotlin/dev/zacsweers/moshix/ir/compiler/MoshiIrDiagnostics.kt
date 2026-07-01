// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.ir.compiler

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.KtDiagnosticRenderers.TO_STRING
import org.jetbrains.kotlin.diagnostics.error1
import org.jetbrains.kotlin.diagnostics.errorWithoutSource
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.psi.KtElement

internal object MoshiDiagnostics : KtDiagnosticsContainer() {
  val MOSHI_ERROR by error1<KtElement, String>()
  val SOURCELESS_MOSHI_ERROR by errorWithoutSource()

  override fun getRendererFactory(): BaseDiagnosticRendererFactory = MoshiIrDiagnosticMessages
}

private object MoshiIrDiagnosticMessages : BaseDiagnosticRendererFactory() {
  override val MAP by
    KtDiagnosticFactoryToRendererMap("MoshiIr") { map ->
      map.put(MoshiDiagnostics.MOSHI_ERROR, "{0}", TO_STRING)
      map.put(MoshiDiagnostics.SOURCELESS_MOSHI_ERROR, "{0}")
    }
}
