// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package com.squareup.moshi.kotlin.codegen.test.extra

import com.squareup.moshi.Json

public abstract class AbstractClassInModuleA {
  // Ignored/transient to ensure processor sees them across module boundaries.
  @Transient private lateinit var lateinitTransient: String
  @Transient private var regularTransient: String = "regularTransient"
  // Note that we target the field because otherwise it is stored on the synthetic holder method for
  // annotations, which isn't visible from kapt
  @field:Json(ignore = true) private lateinit var lateinitIgnored: String
  @field:Json(ignore = true) private var regularIgnored: String = "regularIgnored"
}
