package dev.zacsweers.moshix.sealed.sample

import com.squareup.moshi.JsonClass
import dev.zacsweers.moshix.sealed.annotations.DefaultObject
import dev.zacsweers.moshix.sealed.annotations.TypeLabel

@JsonClass(generateAdapter = true, generator = "sealed:type")
sealed class Message {

  @TypeLabel("success")
  @JsonClass(generateAdapter = true)
  data class Success(val value: String) : Message()

  @TypeLabel("error")
  @JsonClass(generateAdapter = true)
  data class Error(val error_logs: Map<String, Any>) : Message()

  @DefaultObject
  object Unknown : Message()
}
