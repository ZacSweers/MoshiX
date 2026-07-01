// RUN_PIPELINE_TILL: FIR2IR
// DISABLE_GENERATED_FIR_TAGS
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

package test

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class PrivateConstructor <!MOSHI_ERROR!>private constructor(var a: Int, var b: Int)<!> {
  fun a() = a
  fun b() = b

  companion object {
    fun newInstance(a: Int, b: Int) = PrivateConstructor(a, b)
  }
}

