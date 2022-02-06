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
package dev.zacsweers.moshix.sealed.sample.test

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.zacsweers.moshix.sealed.annotations.DefaultNull
import dev.zacsweers.moshix.sealed.annotations.TypeLabel
import dev.zacsweers.moshix.sealed.reflect.MetadataMoshiSealedJsonAdapterFactory
import dev.zacsweers.moshix.sealed.reflect.MoshiSealedJsonAdapterFactory
import dev.zacsweers.moshix.sealed.sample.Message
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
@ExperimentalStdlibApi
class MessageTest(type: Type) {

  enum class Type(val moshi: Moshi = Moshi.Builder().build()) {
    REFLECT(
        moshi =
            Moshi.Builder()
                .add(MoshiSealedJsonAdapterFactory())
                .addLast(KotlinJsonAdapterFactory())
                .build()),
    METADATA_REFLECT(
        moshi =
            Moshi.Builder()
                .add(MetadataMoshiSealedJsonAdapterFactory())
                .addLast(KotlinJsonAdapterFactory())
                .build()),
    CODEGEN
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun data(): Collection<Array<*>> {
      return listOf(arrayOf(Type.REFLECT), arrayOf(Type.METADATA_REFLECT), arrayOf(Type.CODEGEN))
    }
  }

  private val moshi: Moshi = type.moshi

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

  @Test
  fun nestedMessageTypes() {
    val adapter = moshi.adapter<NestedMessageTypes>()
    // Basic nested sealed support
    assertThat(adapter.fromJson("{\"type\":\"success_int\",\"value\":3}"))
        .isEqualTo(NestedMessageTypes.Success.SuccessInt(3))
    assertThat(adapter.fromJson("{\"type\":\"success_string\",\"value\":\"Okay!\"}"))
        .isEqualTo(NestedMessageTypes.Success.SuccessString("Okay!"))

    // Different label keys
    assertThat(
            adapter.fromJson(
                "{\"type\":\"something_else\",\"second_type\":\"success\",\"value\":\"Okay!\"}"))
        .isEqualTo(NestedMessageTypes.DifferentLabelKey.Success("Okay!"))

    // Adapter for the intermediate type works too
    val intermediateAdapter = moshi.adapter<NestedMessageTypes.Success>()
    assertThat(intermediateAdapter.fromJson("{\"type\":\"success_int\",\"value\":3}"))
        .isEqualTo(NestedMessageTypes.Success.SuccessInt(3))
  }

  @DefaultNull
  @JsonClass(generateAdapter = true, generator = "sealed:type")
  sealed interface NestedMessageTypes {

    sealed class Success : NestedMessageTypes {
      @TypeLabel("success_int")
      @JsonClass(generateAdapter = true)
      data class SuccessInt(val value: Int) : Success()

      @TypeLabel("success_string")
      @JsonClass(generateAdapter = true)
      data class SuccessString(val value: String) : Success()
    }

    @TypeLabel("something_else")
    @JsonClass(generateAdapter = true, generator = "sealed:second_type")
    sealed interface DifferentLabelKey : NestedMessageTypes {
      @TypeLabel("success")
      @JsonClass(generateAdapter = true)
      data class Success(val value: String) : DifferentLabelKey

      @TypeLabel("error")
      @JsonClass(generateAdapter = true)
      data class Error(val error_logs: Map<String, Any>) : DifferentLabelKey
    }

    @TypeLabel("error")
    @JsonClass(generateAdapter = true)
    data class Error(val error_logs: Map<String, Any>) : NestedMessageTypes
  }
}
