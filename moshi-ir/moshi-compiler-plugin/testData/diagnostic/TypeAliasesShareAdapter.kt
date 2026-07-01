// RUN_PIPELINE_TILL: BACKEND
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

package test

import com.squareup.moshi.JsonClass

typealias FirstName = String

typealias LastName = String

@JsonClass(generateAdapter = true)
data class Person(val firstName: FirstName, val lastName: LastName, val hairColor: String)

