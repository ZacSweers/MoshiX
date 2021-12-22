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
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.adapter
import java.lang.reflect.InvocationTargetException
import java.util.Locale
import kotlin.reflect.javaType
import kotlin.reflect.typeOf
import org.junit.Test

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
      Class.forName("dev.zacsweers.moshix.ir.playground.SimpleClassJsonAdapter").constructors[0]
          .newInstance(moshi, arrayOf(typeOf<String>().javaType, typeOf<Int>().javaType))
    } catch (e: InvocationTargetException) {
      assertThat(e.cause)
          .hasMessageThat()
          .contains(
              "TypeVariable mismatch: Expecting 1 type for generic type variables [T], but received 2")
    }

    val instanceWithDefault = adapter.fromJson("""{"b":"there"}""")!!
    assertThat(instanceWithDefault.a).isEqualTo(1)
  }

  @Test
  fun constructorParameterWithQualifier() {
    val moshi = Moshi.Builder().add(UppercaseJsonAdapter()).build()
    val jsonAdapter = moshi.adapter<ConstructorParameterWithQualifier>()

    val encoded = ConstructorParameterWithQualifier("Android", "Banana")
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"a":"ANDROID","b":"Banana"}""")

    val decoded = jsonAdapter.fromJson("""{"a":"Android","b":"Banana"}""")!!
    assertThat(decoded.a).isEqualTo("android")
    assertThat(decoded.b).isEqualTo("Banana")
  }

  @JsonClass(generateAdapter = true)
  class ConstructorParameterWithQualifier(@Uppercase(inFrench = true) val a: String, val b: String)
}

@JsonQualifier annotation class Uppercase(val inFrench: Boolean, val onSundays: Boolean = false)

class UppercaseJsonAdapter {
  @ToJson
  fun toJson(@Uppercase(inFrench = true) s: String): String {
    return s.uppercase(Locale.US)
  }
  @FromJson
  @Uppercase(inFrench = true)
  fun fromJson(s: String): String {
    return s.lowercase(Locale.US)
  }
}

@JsonClass(generateAdapter = true) data class SimpleClass<T>(val a: Int = 1, val b: T)
