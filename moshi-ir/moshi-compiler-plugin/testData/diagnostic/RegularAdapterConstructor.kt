// RUN_PIPELINE_TILL: BACKEND
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

package test

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Value(val value: String, val count: Int = 0)
