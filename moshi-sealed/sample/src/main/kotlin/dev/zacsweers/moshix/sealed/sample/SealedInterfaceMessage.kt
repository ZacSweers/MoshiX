// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.sealed.sample

import com.squareup.moshi.JsonClass
import dev.zacsweers.moshix.sealed.annotations.DefaultObject
import dev.zacsweers.moshix.sealed.annotations.TypeLabel

@JsonClass(generateAdapter = true, generator = "sealed:type")
sealed interface SealedInterfaceMessage {

  @TypeLabel("success", ["successful"])
  @JsonClass(generateAdapter = true)
  data class Success(val value: String) : SealedInterfaceMessage

  @TypeLabel("error")
  @JsonClass(generateAdapter = true)
  data class Error(val error_logs: Map<String, Any>) : SealedInterfaceMessage

  @DefaultObject object Unknown : SealedInterfaceMessage
}
