// RUN_PIPELINE_TILL: BACKEND
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

package test

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import dev.zacsweers.moshix.sealed.annotations.FallbackJsonAdapter
import dev.zacsweers.moshix.sealed.annotations.TypeLabel

@OptIn(ExperimentalStdlibApi::class)
@FallbackJsonAdapter(MessageWithFallbackAdapter.SuccessAdapter::class)
@JsonClass(generateAdapter = true, generator = "sealed:type")
sealed class MessageWithFallbackAdapter {
  @TypeLabel("success", ["successful"])
  @JsonClass(generateAdapter = true)
  data class Success(val value: String) : MessageWithFallbackAdapter()

  @TypeLabel("error")
  @JsonClass(generateAdapter = true)
  data class Error(val error_logs: Map<String, Any>) : MessageWithFallbackAdapter()

  class SuccessAdapter(moshi: Moshi) : JsonAdapter<MessageWithFallbackAdapter>() {
    private val delegate = moshi.adapter<Success>()

    override fun fromJson(reader: JsonReader): MessageWithFallbackAdapter? {
      return delegate.fromJson(reader)
    }

    override fun toJson(writer: JsonWriter, value: MessageWithFallbackAdapter?) {
      throw NotImplementedError()
    }
  }
}

