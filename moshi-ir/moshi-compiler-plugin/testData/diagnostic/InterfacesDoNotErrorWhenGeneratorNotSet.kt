// RUN_PIPELINE_TILL: BACKEND
// DISABLE_GENERATED_FIR_TAGS
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

package test

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true, generator = "customGenerator") interface Interface

