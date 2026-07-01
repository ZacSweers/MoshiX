// RENDER_DIAGNOSTICS_FULL_TEXT

package test

import com.squareup.moshi.JsonClass
import dev.zacsweers.moshix.sealed.annotations.NestedSealed
import dev.zacsweers.moshix.sealed.annotations.TypeLabel

sealed class Parent

<!MOSHI_ERROR!>@NestedSealed
@JsonClass(generateAdapter = true, generator = "sealed:type")
sealed class Child : Parent() {
  @TypeLabel("leaf")
  class Leaf : Child()
}<!>
