// RUN_PIPELINE_TILL: FIR2IR
// DISABLE_GENERATED_FIR_TAGS
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

package test

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import dev.zacsweers.moshix.sealed.annotations.DefaultObject
import dev.zacsweers.moshix.sealed.annotations.FallbackJsonAdapter
import dev.zacsweers.moshix.sealed.annotations.TypeLabel

<!MOSHI_ERROR!>class BaseTypeFallback private constructor() : JsonAdapter<String>() {
  override fun fromJson(reader: JsonReader): String? {
    return null
  }

  override fun toJson(writer: JsonWriter, value: String?) {}
}<!>

@FallbackJsonAdapter(BaseTypeFallback::class)
@JsonClass(generateAdapter = true, generator = "sealed:type")
sealed class BaseType {
  @TypeLabel("a")
  class TypeA : BaseType()

  @DefaultObject
  object TypeB : BaseType()
}

