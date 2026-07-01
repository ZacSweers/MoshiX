// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package test

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import dev.zacsweers.moshix.sealed.annotations.DefaultNull
import dev.zacsweers.moshix.sealed.annotations.FallbackJsonAdapter
import dev.zacsweers.moshix.sealed.annotations.TypeLabel

class BaseTypeFallback : JsonAdapter<String>() {
  override fun fromJson(reader: JsonReader): String? {
    return null
  }

  override fun toJson(writer: JsonWriter, value: String?) {}
}

<!MOSHI_ERROR!>@FallbackJsonAdapter(BaseTypeFallback::class)
@DefaultNull
@JsonClass(generateAdapter = true, generator = "sealed:type")
sealed class BaseType {
  @TypeLabel("a")
  class TypeA : BaseType()
}<!>

