package io.sweers.moshisealed.sample.test

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.sweers.moshisealed.reflect.MoshiSealedJsonAdapterFactory
import io.sweers.moshisealed.sample.Message
import org.junit.Test
import java.util.Collections

class MessageTest {

  @Test
  fun polymorphic_reflect() {
    val moshi = Moshi.Builder()
        .add(MoshiSealedJsonAdapterFactory())
        .add(KotlinJsonAdapterFactory())
        .build()

    val adapter = moshi.adapter<Message>(Message::class.java)
    assertPolymorphicBehavior(adapter)
  }

  @Test
  fun polymorphic_codegen() {
    val moshi = Moshi.Builder()
        .build()

    val adapter = moshi.adapter<Message>(Message::class.java)
    assertPolymorphicBehavior(adapter)
  }

  private fun assertPolymorphicBehavior(adapter: JsonAdapter<Message>) {
    assertThat(adapter.fromJson("{\"type\":\"success\",\"value\":\"Okay!\"}"))
        .isEqualTo(Message.Success("Okay!"))
    assertThat(adapter.fromJson("{\"type\":\"error\",\"error_logs\":{\"order\":66}}"))
        .isEqualTo(Message.Error(Collections.singletonMap<String, Any>("order", 66.0)))
    assertThat(adapter.fromJson("{\"type\":\"taco\",\"junkdata\":100}"))
        .isSameInstanceAs(Message.Unknown)
  }
}
