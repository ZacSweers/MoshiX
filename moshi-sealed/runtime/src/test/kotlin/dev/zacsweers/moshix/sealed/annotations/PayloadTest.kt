package dev.zacsweers.moshix.sealed.annotations

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okio.Buffer
import org.junit.Test

class PayloadTest {

  @Payload("type")
  data class ResponseType(
    val type: String,
    val payload: PayloadData
  )

  data class PayloadData(val foo: String)

  private val moshi = Moshi.Builder()
    .add(Payload.Factory)
    .addLast(KotlinJsonAdapterFactory())
    .build()

  @Test
  fun happyPath() {
    val json = """
      {
        "type": "foo",
        "payload": {
          "foo": "bar"
        }
      }
      """

    val buffer = Buffer()
    buffer.writeUtf8(json)
    val reader = JsonReader.of(buffer)
    moshi.adapter(ResponseType::class.java).fromJson(reader)
    // Normally the later adapter would null out the tag here, but we're testing the factory
    assertThat(reader.tag(PayloadTypeHint::class.java)?.type).isEqualTo("foo")
  }
}