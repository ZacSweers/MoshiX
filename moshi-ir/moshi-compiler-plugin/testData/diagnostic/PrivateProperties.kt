// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package test

import com.squareup.moshi.JsonClass

<!MOSHI_ERROR!>@JsonClass(generateAdapter = true)
class PrivateProperties {
  private var a: Int = -1
  private var b: Int = -1
}<!>

