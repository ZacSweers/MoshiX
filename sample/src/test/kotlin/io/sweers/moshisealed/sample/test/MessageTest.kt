package io.sweers.moshisealed.sample.test

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import io.sweers.moshisealed.sample.Message
import org.junit.Test
import java.util.Collections

class MessageTest {

  // TODO failure cases, likely need to run in moshi-kotlin/test directly because code gen could
  // report failures at compile time, and we want to catch both
  @Test
  fun polymorphic() {
    val moshi = Moshi.Builder()
//        .add(MoshiSealedJsonAdapterFactory())
//        .add(KotlinJsonAdapterFactory())
        .build()

    val adapter = moshi.adapter<Message>(Message::class.java)

    assertThat(adapter.fromJson("{\"type\":\"success\",\"value\":\"Okay!\"}"))
        .isEqualTo(Message.Success("Okay!"))
    assertThat(adapter.fromJson("{\"type\":\"error\",\"error_logs\":{\"order\":66}}"))
        .isEqualTo(Message.Error(Collections.singletonMap<String, Any>("order", 66.0)))
    assertThat(adapter.fromJson("{\"type\":\"taco\",\"junkdata\":100}"))
        .isSameInstanceAs(Message.Unknown)
  }
}
