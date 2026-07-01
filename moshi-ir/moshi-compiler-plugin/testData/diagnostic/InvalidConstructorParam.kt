// RUN_PIPELINE_TILL: FIR2IR
// DISABLE_GENERATED_FIR_TAGS
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

package test

import com.squareup.moshi.JsonClass
import dev.zacsweers.moshix.sealed.annotations.FallbackJsonAdapter
import dev.zacsweers.moshix.sealed.annotations.TypeLabel
import dev.zacsweers.moshix.sealed.runtime.internal.ObjectJsonAdapter

<!MOSHI_ERROR!>@FallbackJsonAdapter(ObjectJsonAdapter::class)
@JsonClass(generateAdapter = true, generator = "sealed:type")
sealed class BaseType {
  @TypeLabel("a")
  class TypeA : BaseType()
}<!>

