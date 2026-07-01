// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package test

import com.squareup.moshi.JsonClass

class Outer {
  <!MOSHI_ERROR!>@JsonClass(generateAdapter = true)
  inner class InnerClass(val a: Int)<!>
}

