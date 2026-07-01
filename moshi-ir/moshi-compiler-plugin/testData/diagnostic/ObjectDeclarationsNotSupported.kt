// RENDER_DIAGNOSTICS_FULL_TEXT

package test

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
<!MOSHI_ERROR!>object ObjectDeclaration<!> {
  var a = 5
}
