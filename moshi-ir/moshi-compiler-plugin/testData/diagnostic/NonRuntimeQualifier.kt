// RUN_PIPELINE_TILL: FIR2IR
// DISABLE_GENERATED_FIR_TAGS
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

package test

import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonQualifier
import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.FIELD
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.annotation.Retention
import kotlin.annotation.Target

@Retention(BINARY)
@Target(PROPERTY, FIELD)
@JsonQualifier
annotation class UpperCase

@JsonClass(generateAdapter = true)
class ClassWithQualifier(<!MOSHI_ERROR!>@UpperCase<!> val a: Int)

