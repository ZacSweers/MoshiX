// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.sealed.runtime.internal

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter

public class ObjectJsonAdapter<T : Any>(private val instance: T) : JsonAdapter<T>() {
  override fun fromJson(reader: JsonReader): T {
    reader.beginObject()
    while (reader.hasNext()) {
      reader.skipName()
      reader.skipValue()
    }
    reader.endObject()
    return instance
  }

  override fun toJson(writer: JsonWriter, value: T?) {
    writer.beginObject().endObject()
  }

  override fun toString(): String {
    return "ObjectJsonAdapter<" + instance.javaClass.canonicalName + ">"
  }
}
