// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package test

import com.squareup.moshi.JsonClass
import java.util.Date

<!MOSHI_ERROR!>@JsonClass(generateAdapter = true)
class ExtendsPlatformClass(var a: Int) : Date()<!>

