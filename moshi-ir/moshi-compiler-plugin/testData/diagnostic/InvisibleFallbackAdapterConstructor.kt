// RENDER_DIAGNOSTICS_FULL_TEXT

package test

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import dev.zacsweers.moshix.sealed.annotations.DefaultObject
import dev.zacsweers.moshix.sealed.annotations.FallbackJsonAdapter
import dev.zacsweers.moshix.sealed.annotations.TypeLabel

class BaseTypeFallback private constructor() : JsonAdapter<String>() {
  override fun fromJson(reader: JsonReader): String? {
    return null
  }

  override fun toJson(writer: JsonWriter, value: String?) {}
}

<!MOSHI_ERROR!>@FallbackJsonAdapter(BaseTypeFallback::class)
@JsonClass(generateAdapter = true, generator = "sealed:type")
sealed class BaseType {
  @TypeLabel("a")
  class TypeA : BaseType()

  @DefaultObject
  object TypeB : BaseType()
}<!>
