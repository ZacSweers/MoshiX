// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package test

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class IgnoredDefaults(
  @Json(ignore = true) val unit: Unit = Unit,
  @Json(ignore = true) val nothing: Nothing = error("unused"),
  @Json(ignore = true) val void: java.lang.Void = error("unused"),
  val value: String = "value",
)
