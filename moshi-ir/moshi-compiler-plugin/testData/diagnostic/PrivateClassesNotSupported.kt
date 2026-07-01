// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package test

import com.squareup.moshi.JsonClass

<!MOSHI_ERROR!>@JsonClass(generateAdapter = true)
private class PrivateClass(val a: Int)<!>

