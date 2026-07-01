// RENDER_DIAGNOSTICS_FULL_TEXT

package test

import com.squareup.moshi.JsonClass

<!MOSHI_ERROR!>@JsonClass(generateAdapter = true)
class PrivateProperties {
  private var a: Int = -1
  private var b: Int = -1
}<!>
