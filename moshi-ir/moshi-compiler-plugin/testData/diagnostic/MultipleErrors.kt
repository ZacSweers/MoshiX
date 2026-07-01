// RENDER_DIAGNOSTICS_FULL_TEXT

package test

import com.squareup.moshi.JsonClass

<!MOSHI_ERROR!>@JsonClass(generateAdapter = true)
class Class1(private var a: Int, private var b: Int)<!>

<!MOSHI_ERROR!>@JsonClass(generateAdapter = true)
class Class2(private var c: Int)<!>
