// RENDER_DIAGNOSTICS_FULL_TEXT

package test

import com.squareup.moshi.JsonClass
import java.util.Date

<!MOSHI_ERROR!>@JsonClass(generateAdapter = true)
class ExtendsPlatformClass(var a: Int) : Date()<!>
