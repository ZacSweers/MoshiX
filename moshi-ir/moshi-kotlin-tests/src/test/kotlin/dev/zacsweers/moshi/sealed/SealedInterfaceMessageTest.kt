// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshi.sealed

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import dev.zacsweers.moshix.sealed.annotations.DefaultNull
import dev.zacsweers.moshix.sealed.annotations.TypeLabel
import org.junit.Test

@ExperimentalStdlibApi
class SealedInterfaceMessageTest {
  private val moshi: Moshi = Moshi.Builder().build()

  @Test
  fun assertDefaultBehavior() {
    val adapter = moshi.adapter<SealedInterfaceMessage>()
    assertPolymorphicBehavior(
      adapter,
      SealedInterfaceMessage.Success("Okay!"),
      SealedInterfaceMessage.Error(mapOf("order" to 66.0)),
      SealedInterfaceMessage.Unknown,
    )
  }

  @Test
  fun assertDefaultNullBehavior() {
    val adapter = moshi.adapter<MessageWithNullDefault>()
    assertPolymorphicBehavior(
      adapter,
      MessageWithNullDefault.Success("Okay!"),
      MessageWithNullDefault.Error(mapOf("order" to 66.0)),
      null,
    )
  }

  @Test
  fun assertNoDefaultBehavior() {
    val adapter = moshi.adapter<MessageWithNoDefault>()
    assertPolymorphicBehavior(
      adapter,
      MessageWithNoDefault.Success("Okay!"),
      MessageWithNoDefault.Error(mapOf("order" to 66.0)),
      null,
    )
  }

  private fun <T> assertPolymorphicBehavior(
    adapter: JsonAdapter<T>,
    success: T,
    error: T,
    defaultInstance: T?,
  ) {
    assertThat(adapter.fromJson("{\"type\":\"success\",\"value\":\"Okay!\"}")).isEqualTo(success)
    // Test alternates
    assertThat(adapter.fromJson("{\"type\":\"successful\",\"value\":\"Okay!\"}")).isEqualTo(success)
    assertThat(adapter.fromJson("{\"type\":\"error\",\"error_logs\":{\"order\":66}}"))
      .isEqualTo(error)
    assertThat(adapter.fromJson("{\"type\":\"taco\",\"junkdata\":100}"))
      .isSameInstanceAs(defaultInstance)
  }

  @DefaultNull
  @JsonClass(generateAdapter = true, generator = "sealed:type")
  sealed class MessageWithNullDefault {

    @TypeLabel("success", ["successful"])
    @JsonClass(generateAdapter = true)
    data class Success(val value: String) : MessageWithNullDefault()

    @TypeLabel("error")
    @JsonClass(generateAdapter = true)
    data class Error(val error_logs: Map<String, Any>) : MessageWithNullDefault()
  }

  @DefaultNull
  @JsonClass(generateAdapter = true, generator = "sealed:type")
  sealed interface MessageWithNoDefault {

    @TypeLabel("success", ["successful"])
    @JsonClass(generateAdapter = true)
    data class Success(val value: String) : MessageWithNoDefault

    @TypeLabel("error")
    @JsonClass(generateAdapter = true)
    data class Error(val error_logs: Map<String, Any>) : MessageWithNoDefault
  }

  @DefaultNull
  @JsonClass(generateAdapter = true, generator = "sealed:type")
  internal sealed interface MessageWithInternalVisibilityModifier {

    @TypeLabel("success", ["successful"])
    @JsonClass(generateAdapter = true)
    data class Success(val value: String) : MessageWithInternalVisibilityModifier

    @TypeLabel("error")
    @JsonClass(generateAdapter = true)
    data class Error(val error_logs: Map<String, Any>) : MessageWithInternalVisibilityModifier
  }
}
