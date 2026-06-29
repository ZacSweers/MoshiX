// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package com.squareup.moshi.kotlin.codegen

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.kotlin.codegen.GeneratedAdaptersTest.CustomGeneratedClass

// This also tests custom generated types with no moshi constructor
class GeneratedAdaptersTest_CustomGeneratedClassJsonAdapter : JsonAdapter<CustomGeneratedClass>() {
  override fun fromJson(reader: JsonReader): CustomGeneratedClass? {
    TODO()
  }

  override fun toJson(writer: JsonWriter, value: CustomGeneratedClass?) {
    TODO()
  }
}
