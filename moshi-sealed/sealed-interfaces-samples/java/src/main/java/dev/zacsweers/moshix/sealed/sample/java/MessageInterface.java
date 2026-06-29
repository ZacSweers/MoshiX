// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.sealed.sample.java;

import com.squareup.moshi.JsonClass;
import dev.zacsweers.moshix.sealed.annotations.TypeLabel;
import java.util.Map;

// @DefaultObject is not possible in java
@JsonClass(generateAdapter = false, generator = "sealed:type")
sealed interface MessageInterface permits MessageInterface.Success, MessageInterface.Error {

  @TypeLabel(
      label = "success",
      alternateLabels = {"successful"})
  final record Success(String value) implements MessageInterface {}

  @TypeLabel(label = "error")
  final record Error(Map<String, Object> error_logs) implements MessageInterface {}
}
