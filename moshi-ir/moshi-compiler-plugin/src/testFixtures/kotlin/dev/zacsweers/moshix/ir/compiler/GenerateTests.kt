// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.ir.compiler

import org.jetbrains.kotlin.generators.dsl.junit5.generateTestGroupSuiteWithJUnit5

fun main() {
  generateTestGroupSuiteWithJUnit5 {
    testGroup(
      testDataRoot = "moshi-ir/moshi-compiler-plugin/testData",
      testsRoot = "moshi-ir/moshi-compiler-plugin/test-gen/java",
    ) {
      testClass<AbstractMoshiDiagnosticTest> { model("diagnostic") }
    }
  }
}
