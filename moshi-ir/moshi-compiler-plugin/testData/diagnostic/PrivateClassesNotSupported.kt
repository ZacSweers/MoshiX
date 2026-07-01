// RENDER_DIAGNOSTICS_FULL_TEXT

package test

import com.squareup.moshi.JsonClass

<!MOSHI_ERROR!>@JsonClass(generateAdapter = true)
private class PrivateClass(val a: Int)<!>
