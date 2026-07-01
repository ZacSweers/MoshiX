// RENDER_DIAGNOSTICS_FULL_TEXT

package test

import com.squareup.moshi.JsonClass

class Outer {
  <!MOSHI_ERROR!>@JsonClass(generateAdapter = true)
  inner class InnerClass(val a: Int)<!>
}
