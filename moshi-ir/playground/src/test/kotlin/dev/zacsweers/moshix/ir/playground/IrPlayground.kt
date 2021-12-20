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
@file:OptIn(ExperimentalStdlibApi::class)

package dev.zacsweers.moshix.ir.playground

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import org.junit.Test
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.javaType
import kotlin.reflect.typeOf

class IrPlayground {

  @Test
  fun simple() {
    val moshi = Moshi.Builder().build()
    val adapter = moshi.adapter<SimpleClass<String>>()
    assertThat(adapter.toString()).isEqualTo("JsonAdapter(SimpleClass).nullSafe()")

    val instance = adapter.fromJson("""{"a":3,"b":"there"}""")
    println(instance.toString())
    println(adapter.toJson(SimpleClass(3, "there")))

    // Ensure types check works
    try {
      Class.forName("dev.zacsweers.moshix.ir.playground.SimpleClassJsonAdapter")
        .constructors[0]
        .newInstance(moshi, arrayOf(typeOf<String>().javaType, typeOf<Int>().javaType))
    } catch (e: InvocationTargetException) {
      assertThat(e.cause).hasMessageThat().contains("TypeVariable mismatch: Expecting 1 type for generic type variables [T], but received 2")
    }
  }
}

@JsonClass(generateAdapter = true)
data class SimpleClass<T>(val a: Int?, val b: T)
