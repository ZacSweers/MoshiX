// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.sealed.sample.java;

import com.squareup.moshi.JsonClass;
import dev.zacsweers.moshix.sealed.annotations.DefaultNull;
import dev.zacsweers.moshix.sealed.annotations.TypeLabel;
import java.util.Map;

// @DefaultObject is not possible in java
@DefaultNull
@JsonClass(generateAdapter = false, generator = "sealed:type")
sealed interface MessageWithNullDefault
    permits MessageWithNullDefault.Success, MessageWithNullDefault.Error {

  @TypeLabel(
      label = "success",
      alternateLabels = {"successful"})
  final record Success(String value) implements MessageWithNullDefault {}

  @TypeLabel(label = "error")
  final record Error(Map<String, Object> error_logs) implements MessageWithNullDefault {}
}
