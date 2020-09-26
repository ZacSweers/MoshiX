/*
 * Copyright (C) 2020 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.zacsweers.moshix

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Test
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.reflect.typeOf

@JsonQualifier
@Retention(RUNTIME)
annotation class TestAnnotation1

@JsonQualifier
@Retention(RUNTIME)
annotation class TestAnnotation2

@TestAnnotation1
@TestAnnotation2
class KotlinExtensionsTest {

  @Test
  fun nextAnnotationsShouldWork() {
    val annotations = KotlinExtensionsTest::class.java.annotations
      .filterTo(mutableSetOf()) {
        it.annotationClass.java.isAnnotationPresent(JsonQualifier::class.java)
      }
    assertThat(annotations).hasSize(2)
    val next = annotations.nextAnnotations<TestAnnotation2>()
    checkNotNull(next)
    assertThat(next).hasSize(1)
    assertThat(next.first() is TestAnnotation1).isTrue()
  }

  @Test
  fun arrayType() {
    val stringArray = String::class.asArrayType()
    check(stringArray.genericComponentType == String::class.java)

    val stringListType = typeOf<List<String>>()
    val stringListArray = stringListType.asArrayType()
    val expected = Types.arrayOf(Types.newParameterizedType(List::class.java, String::class.java))
    assertThat(expected).isEqualTo(stringListArray)
  }

  @Test
  fun addAdapterInferred() {
    // An adapter that always returns -1
    val customIntdapter = object : JsonAdapter<Int>() {
      override fun fromJson(reader: JsonReader): Int? {
        reader.skipValue()
        return -1
      }

      override fun toJson(writer: JsonWriter, value: Int?) {
        throw NotImplementedError()
      }
    }
    val moshi = Moshi.Builder()
      .addAdapter(customIntdapter)
      .build()

    assertThat(moshi.adapter<Int>().fromJson("5")).isEqualTo(-1)
  }

  @Test
  fun addAdapterInferred_parameterized() {
    // An adapter that always returns listOf(-1)
    val customIntListAdapter = object : JsonAdapter<List<Int>>() {
      override fun fromJson(reader: JsonReader): List<Int>? {
        reader.skipValue()
        return listOf(-1)
      }

      override fun toJson(writer: JsonWriter, value: List<Int>?) {
        throw NotImplementedError()
      }
    }
    val moshi = Moshi.Builder()
      .addAdapter(customIntListAdapter)
      .build()

    assertThat(moshi.adapter<List<Int>>().fromJson("[5]")).isEqualTo(listOf(-1))
  }

  @Test
  fun arrays() {
    val adapter = Moshi.Builder().build().adapter<Array<Int>>()
    val json = "[1,2,3]"
    val instance = adapter.fromJson(json)
    checkNotNull(instance)
    assertThat(instance).isEqualTo(arrayOf(1, 2, 3))
  }
}