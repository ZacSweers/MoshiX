// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package test

import com.squareup.moshi.JsonClass

typealias FirstName = String

typealias LastName = String

@JsonClass(generateAdapter = true)
data class Person(val firstName: FirstName, val lastName: LastName, val hairColor: String)
