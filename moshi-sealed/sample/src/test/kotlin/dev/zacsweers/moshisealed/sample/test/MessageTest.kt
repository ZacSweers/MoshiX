package dev.zacsweers.moshisealed.sample.test

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.zacsweers.moshisealed.annotations.DefaultNull
import dev.zacsweers.moshisealed.annotations.TypeLabel
import dev.zacsweers.moshisealed.reflect.MoshiSealedJsonAdapterFactory
import dev.zacsweers.moshisealed.sample.Message
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
@ExperimentalStdlibApi
class MessageTest(type: Type) {

  enum class Type(val moshi: Moshi = Moshi.Builder().build()) {
    REFLECT(
        moshi = Moshi.Builder()
            .add(MoshiSealedJsonAdapterFactory())
            .add(KotlinJsonAdapterFactory())
            .build()
    )
    ,
    CODEGEN
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data(): Collection<Array<*>> {
      return listOf(
          arrayOf(Type.REFLECT),
          arrayOf(Type.CODEGEN)
      )
    }
  }

  private val moshi: Moshi = type.moshi

  @Test
  fun assertDefaultBehavior() {
    val adapter = moshi.adapter(Message::class.java)
    assertPolymorphicBehavior(
        adapter,
        Message.Success("Okay!"),
        Message.Error(mapOf("order" to 66.0)),
        Message.Unknown
    )
  }

  @Test
  fun assertDefaultNullBehavior() {
    val adapter = moshi.adapter(MessageWithNullDefault::class.java)
    assertPolymorphicBehavior(
        adapter,
        MessageWithNullDefault.Success("Okay!"),
        MessageWithNullDefault.Error(mapOf("order" to 66.0)),
        null
    )
  }

  @Test
  fun assertNoDefaultBehavior() {
    val adapter = moshi.adapter(MessageWithNoDefault::class.java)
    assertPolymorphicBehavior(
        adapter,
        MessageWithNoDefault.Success("Okay!"),
        MessageWithNoDefault.Error(mapOf("order" to 66.0)),
        null
    )
  }

  private fun <T> assertPolymorphicBehavior(
      adapter: JsonAdapter<T>,
      success: T,
      error: T,
      defaultInstance: T?
  ) {
    assertThat(adapter.fromJson("{\"type\":\"success\",\"value\":\"Okay!\"}"))
        .isEqualTo(success)
    assertThat(adapter.fromJson("{\"type\":\"error\",\"error_logs\":{\"order\":66}}"))
        .isEqualTo(error)
    assertThat(adapter.fromJson("{\"type\":\"taco\",\"junkdata\":100}"))
        .isSameInstanceAs(defaultInstance)
  }

  @DefaultNull
  @JsonClass(generateAdapter = true, generator = "sealed:type")
  sealed class MessageWithNullDefault {

    @TypeLabel("success")
    @JsonClass(generateAdapter = true)
    data class Success(val value: String) : MessageWithNullDefault()

    @TypeLabel("error")
    @JsonClass(generateAdapter = true)
    data class Error(val error_logs: Map<String, Any>) : MessageWithNullDefault()
  }

  @DefaultNull
  @JsonClass(generateAdapter = true, generator = "sealed:type")
  sealed class MessageWithNoDefault {

    @TypeLabel("success")
    @JsonClass(generateAdapter = true)
    data class Success(val value: String) : MessageWithNoDefault()

    @TypeLabel("error")
    @JsonClass(generateAdapter = true)
    data class Error(val error_logs: Map<String, Any>) : MessageWithNoDefault()
  }

  @DefaultNull
  @JsonClass(generateAdapter = true, generator = "sealed:type")
  internal sealed class MessageWithInternalVisibilityModifier {

    @TypeLabel("success")
    @JsonClass(generateAdapter = true)
    internal data class Success(val value: String) : MessageWithInternalVisibilityModifier()

    @TypeLabel("error")
    @JsonClass(generateAdapter = true)
    internal data class Error(val error_logs: Map<String, Any>) : MessageWithInternalVisibilityModifier()
  }
}
