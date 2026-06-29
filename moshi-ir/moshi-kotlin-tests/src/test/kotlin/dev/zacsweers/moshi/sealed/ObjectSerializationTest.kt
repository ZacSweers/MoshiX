// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshi.sealed

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import org.junit.Test

class ObjectSerializationTest {
  private val moshi: Moshi = Moshi.Builder().build()

  @Test
  fun smokeTest() {
    // language=json
    val json =
      """
      {
        "name": "tacoFactory",
        "returnType": { "type": "void" },
        "parameters": {
          "param1": { "type": "int" },
          "param2": { "type": "boolean" }
        }
      }
      """
        .trimIndent()

    val functionSpec = moshi.adapter<FunctionSpec>().fromJson(json)
    checkNotNull(functionSpec)
    assertThat(functionSpec)
      .isEqualTo(
        FunctionSpec(
          name = "tacoFactory",
          returnType = Type.VoidType,
          parameters = mapOf("param1" to Type.IntType, "param2" to Type.BooleanType),
        )
      )
  }
}
