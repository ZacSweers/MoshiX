// RUN_PIPELINE_TILL: BACKEND
// DISABLE_GENERATED_FIR_TAGS
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

package test

import com.squareup.moshi.JsonClass
import dev.zacsweers.moshix.sealed.annotations.NestedSealed
import dev.zacsweers.moshix.sealed.annotations.TypeLabel

@JsonClass(generateAdapter = true, generator = "sealed:type")
sealed interface Foo {
  @NestedSealed sealed interface SuperFoo : Foo

  @JsonClass(generateAdapter = true)
  @TypeLabel("real")
  data class RealFoo(val value: String) : SuperFoo, Foo
}

