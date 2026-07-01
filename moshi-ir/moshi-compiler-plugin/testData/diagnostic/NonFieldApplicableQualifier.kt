// RUN_PIPELINE_TILL: BACKEND
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

package test

import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonQualifier
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.annotation.Retention
import kotlin.annotation.Target

@Retention(RUNTIME) @Target(PROPERTY) @JsonQualifier annotation class UpperCase

@JsonClass(generateAdapter = true) class ClassWithQualifier(@UpperCase val a: Int)

