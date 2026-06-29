// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package com.squareup.moshi.kotlin.codegen.LooksLikeAClass

import com.squareup.moshi.JsonClass

/** https://github.com/square/moshi/issues/783 */
@JsonClass(generateAdapter = true) data class ClassInPackageThatLooksLikeAClass(val foo: String)
