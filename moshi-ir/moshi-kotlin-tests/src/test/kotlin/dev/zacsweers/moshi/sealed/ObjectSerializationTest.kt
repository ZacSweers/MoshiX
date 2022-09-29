/*
 * Copyright (C) 2021 Zac Sweers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
          parameters = mapOf("param1" to Type.IntType, "param2" to Type.BooleanType)
        )
      )
  }
}
