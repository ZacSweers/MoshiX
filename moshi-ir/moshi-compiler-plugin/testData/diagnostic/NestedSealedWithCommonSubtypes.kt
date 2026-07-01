// RUN_PIPELINE_TILL: BACKEND
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

