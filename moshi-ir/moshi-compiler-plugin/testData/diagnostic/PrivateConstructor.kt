// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package test

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class PrivateConstructor <!MOSHI_ERROR!>private constructor(var a: Int, var b: Int)<!> {
  fun a() = a
  fun b() = b

  companion object {
    fun newInstance(a: Int, b: Int) = PrivateConstructor(a, b)
  }
}

