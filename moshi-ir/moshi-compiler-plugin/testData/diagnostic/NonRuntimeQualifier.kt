// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
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

