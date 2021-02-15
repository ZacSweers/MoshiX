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
package dev.zacsweers.moshix.sealed.sample.test

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.zacsweers.moshix.sealed.reflect.MetadataMoshiSealedJsonAdapterFactory
import dev.zacsweers.moshix.sealed.reflect.MoshiSealedJsonAdapterFactory
import dev.zacsweers.moshix.sealed.sample.FunctionSpec
import dev.zacsweers.moshix.sealed.sample.Type.BooleanType
import dev.zacsweers.moshix.sealed.sample.Type.IntType
import dev.zacsweers.moshix.sealed.sample.Type.VoidType
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
class ObjectSerializationTest(type: Type) {

  enum class Type(val moshi: Moshi = Moshi.Builder().build()) {
    REFLECT(
      moshi = Moshi.Builder()
        .add(MoshiSealedJsonAdapterFactory())
        .addLast(KotlinJsonAdapterFactory())
        .build()
    ),
    METADATA_REFLECT(
      moshi = Moshi.Builder()
        .add(MetadataMoshiSealedJsonAdapterFactory())
        .addLast(KotlinJsonAdapterFactory())
        .build()
    ),
    CODEGEN
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun data(): Collection<Array<*>> {
      return listOf(
        arrayOf(Type.REFLECT),
        arrayOf(Type.METADATA_REFLECT),
        arrayOf(Type.CODEGEN)
      )
    }
  }

  private val moshi: Moshi = type.moshi

  @Test
  fun smokeTest() {
    //language=json
    val json = """
     {
       "name": "tacoFactory",
       "returnType": { "type": "void" },
       "parameters": {
         "param1": { "type": "int" },
         "param2": { "type": "boolean" }
       }
     }
    """.trimIndent()

    val functionSpec = moshi.adapter<FunctionSpec>().fromJson(json)
    checkNotNull(functionSpec)
    assertThat(functionSpec).isEqualTo(
      FunctionSpec(
        name = "tacoFactory",
        returnType = VoidType,
        parameters = mapOf("param1" to IntType, "param2" to BooleanType)
      )
    )
  }
}
