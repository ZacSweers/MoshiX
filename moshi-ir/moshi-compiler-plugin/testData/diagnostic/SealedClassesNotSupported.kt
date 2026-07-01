// RENDER_DIAGNOSTICS_FULL_TEXT

package test

import com.squareup.moshi.JsonClass

<!MOSHI_ERROR!>@JsonClass(generateAdapter = true)
sealed class SealedClass(val a: Int)<!>
