// RUN_PIPELINE_TILL: FIR2IR
// DISABLE_GENERATED_FIR_TAGS
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

package test

import com.squareup.moshi.JsonClass

<!MOSHI_ERROR!>@JsonClass(generateAdapter = true)
interface Interface<!>

