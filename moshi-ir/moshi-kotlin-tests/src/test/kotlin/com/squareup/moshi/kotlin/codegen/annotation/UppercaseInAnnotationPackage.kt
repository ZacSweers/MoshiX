// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package com.squareup.moshi.kotlin.codegen.annotation

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.ToJson
import java.util.Locale

@JsonQualifier annotation class UppercaseInAnnotationPackage

class UppercaseInAnnotationPackageJsonAdapter {
  @ToJson
  fun toJson(@UppercaseInAnnotationPackage s: String): String {
    return s.uppercase(Locale.US)
  }

  @FromJson
  @UppercaseInAnnotationPackage
  fun fromJson(s: String): String {
    return s.lowercase(Locale.US)
  }
}
