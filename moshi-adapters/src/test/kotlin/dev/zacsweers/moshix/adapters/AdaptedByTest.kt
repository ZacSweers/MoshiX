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
package dev.zacsweers.moshix.adapters

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.moshi.rawType
import java.lang.reflect.Type
import org.junit.Test

class AdaptedByTest {

  private val moshi =
    Moshi.Builder().add(AdaptedBy.Factory()).addLast(KotlinJsonAdapterFactory()).build()

  @Test
  fun adapterProperty() {
    val adapter = moshi.adapter<StringAliasHolderAdapter>()
    val instance = adapter.fromJson("{\"alias\":\"value\"}")
    assertThat(instance).isEqualTo(StringAliasHolderAdapter(StringAlias("value")))
  }

  @Test
  fun factoryProperty() {
    val adapter = moshi.adapter<StringAliasHolderFactory>()
    val instance = adapter.fromJson("{\"alias\":\"value\"}")
    assertThat(instance).isEqualTo(StringAliasHolderFactory(StringAlias("value")))
  }

  data class StringAliasHolderAdapter(@AdaptedBy(StringAliasAdapter::class) val alias: StringAlias)

  data class StringAliasHolderFactory(@AdaptedBy(StringAliasFactory::class) val alias: StringAlias)

  data class StringAlias(val value: String)

  class StringAliasFactory : JsonAdapter.Factory {
    override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
      return if (type.rawType == StringAlias::class.java) {
        StringAliasAdapter()
      } else {
        null
      }
    }
  }

  class StringAliasAdapter : JsonAdapter<StringAlias>() {
    override fun fromJson(reader: JsonReader): StringAlias? {
      return StringAlias(reader.nextString())
    }

    override fun toJson(writer: JsonWriter, value: StringAlias?) {
      if (value == null) {
        writer.nullValue()
        return
      }
      writer.value(value.value)
    }
  }

  @Test
  fun annotatedAdapterClass() {
    val adapter = moshi.adapter<AnnotatedStringAlias>()
    val instance = adapter.fromJson("\"value\"")
    assertThat(instance).isEqualTo(AnnotatedStringAlias("value"))
  }

  @AdaptedBy(AnnotatedStringAliasAdapter::class) data class AnnotatedStringAlias(val value: String)

  class AnnotatedStringAliasAdapter : JsonAdapter<AnnotatedStringAlias>() {
    override fun fromJson(reader: JsonReader): AnnotatedStringAlias? {
      return AnnotatedStringAlias(reader.nextString())
    }

    override fun toJson(writer: JsonWriter, value: AnnotatedStringAlias?) {
      if (value == null) {
        writer.nullValue()
        return
      }
      writer.value(value.value)
    }
  }

  @Test
  fun annotatedFactoryClass() {
    val adapter = moshi.adapter<AnnotatedFactoryStringAlias>()
    val instance = adapter.fromJson("\"value\"")
    assertThat(instance).isEqualTo(AnnotatedFactoryStringAlias("value"))
  }

  @AdaptedBy(AnnotatedFactoryStringAliasFactory::class)
  data class AnnotatedFactoryStringAlias(val value: String)

  class AnnotatedFactoryStringAliasFactory : JsonAdapter.Factory {
    override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
      return if (type.rawType == AnnotatedFactoryStringAlias::class.java) {
        AnnotatedFactoryStringAliasAdapter()
      } else {
        null
      }
    }
  }

  class AnnotatedFactoryStringAliasAdapter : JsonAdapter<AnnotatedFactoryStringAlias>() {
    override fun fromJson(reader: JsonReader): AnnotatedFactoryStringAlias? {
      return AnnotatedFactoryStringAlias(reader.nextString())
    }

    override fun toJson(writer: JsonWriter, value: AnnotatedFactoryStringAlias?) {
      if (value == null) {
        writer.nullValue()
        return
      }
      writer.value(value.value)
    }
  }

  @Test
  fun classUsingAnnotatedClassesAsProperties() {
    val adapter = moshi.adapter<ClassUsingAnnotatedClasses>()
    val instance = adapter.fromJson("{\"alias1\":\"value\",\"alias2\":\"value\"}")
    assertThat(instance)
      .isEqualTo(
        ClassUsingAnnotatedClasses(
          AnnotatedStringAlias("value"),
          AnnotatedFactoryStringAlias("value"),
        )
      )
  }

  data class ClassUsingAnnotatedClasses(
    val alias1: AnnotatedStringAlias,
    val alias2: AnnotatedFactoryStringAlias,
  )

  @Test
  fun nullSafeHandling() {
    val adapter = moshi.adapter(NullHandlingStringAlias::class.java)
    val instance = adapter.fromJson("null")
    assertThat(instance).isEqualTo(NullHandlingStringAlias("null"))
  }

  @AdaptedBy(NullHandlingStringAliasAdapter::class, nullSafe = false)
  data class NullHandlingStringAlias(val value: String)

  class NullHandlingStringAliasAdapter : JsonAdapter<NullHandlingStringAlias>() {
    override fun fromJson(reader: JsonReader): NullHandlingStringAlias? {
      val value =
        if (reader.peek() == JsonReader.Token.NULL) {
          reader.nextNull<String>()
          "null"
        } else {
          reader.nextString()
        }
      return NullHandlingStringAlias(value)
    }

    override fun toJson(writer: JsonWriter, value: NullHandlingStringAlias?) {
      if (value == null) {
        writer.nullValue()
        return
      }
      writer.value(value.value)
    }
  }
  // TODO
  //  nullsafe
}
