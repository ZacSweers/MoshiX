/*
 * Copyright (C) 2020 Zac Sweers
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

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.nextAnnotations
import okio.BufferedSource
import java.lang.reflect.Type
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.FIELD
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY

/**
 * A [JsonQualifier] for use with [String] properties to indicate that their value should be
 * propagated with the raw JSON string representation of that property rather than a decoded type.
 *
 * Usage:
 * ```
 * val moshi = Moshi.Builder()
 *   .add(JsonString.Factory())
 *   .build()
 *
 * @JsonClass(generateAdapter = true)
 * data class Message(
 *   val type: String,
 *   @JsonString val data: String
 * )
 * ```
 */
@JsonQualifier
@Retention(RUNTIME)
@Target(PROPERTY, FIELD, FUNCTION)
public annotation class JsonString {
  public class Factory : JsonAdapter.Factory {
    override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
      if (type != String::class.java) return null
      annotations.nextAnnotations<JsonString>() ?: return null
      return JsonStringJsonAdapter().nullSafe()
    }

    private class JsonStringJsonAdapter : JsonAdapter<String>() {
      override fun fromJson(reader: JsonReader): String =
        reader.nextSource().use(BufferedSource::readUtf8)

      override fun toJson(writer: JsonWriter, value: String?) {
        writer.valueSink().use { sink -> sink.writeUtf8(checkNotNull(value)) }
      }
    }
  }
}
