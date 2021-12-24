/*
 * Copyright (C) 2021 Zac Sweers
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
class MessageTest {
  private val moshi: Moshi = Moshi.Builder().build()

  @Test
  fun assertDefaultBehavior() {
    val adapter = moshi.adapter<Message>()
    assertPolymorphicBehavior(
        adapter, Message.Success("Okay!"), Message.Error(mapOf("order" to 66.0)), Message.Unknown)
  }

  @Test
  fun assertDefaultNullBehavior() {
    val adapter = moshi.adapter<MessageWithNullDefault>()
    assertPolymorphicBehavior(
        adapter,
        MessageWithNullDefault.Success("Okay!"),
        MessageWithNullDefault.Error(mapOf("order" to 66.0)),
        null)
  }

  @Test
  fun assertNoDefaultBehavior() {
    val adapter = moshi.adapter<MessageWithNoDefault>()
    assertPolymorphicBehavior(
        adapter,
        MessageWithNoDefault.Success("Okay!"),
        MessageWithNoDefault.Error(mapOf("order" to 66.0)),
        null)
  }

  private fun <T> assertPolymorphicBehavior(
      adapter: JsonAdapter<T>,
      success: T,
      error: T,
      defaultInstance: T?
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
  sealed class MessageWithNoDefault {

    @TypeLabel("success", ["successful"])
    @JsonClass(generateAdapter = true)
    data class Success(val value: String) : MessageWithNoDefault()

    @TypeLabel("error")
    @JsonClass(generateAdapter = true)
    data class Error(val error_logs: Map<String, Any>) : MessageWithNoDefault()
  }

  @DefaultNull
  @JsonClass(generateAdapter = true, generator = "sealed:type")
  internal sealed class MessageWithInternalVisibilityModifier {

    @TypeLabel("success", ["successful"])
    @JsonClass(generateAdapter = true)
    internal data class Success(val value: String) : MessageWithInternalVisibilityModifier()

    @TypeLabel("error")
    @JsonClass(generateAdapter = true)
    internal data class Error(val error_logs: Map<String, Any>) :
        MessageWithInternalVisibilityModifier()
  }
}
