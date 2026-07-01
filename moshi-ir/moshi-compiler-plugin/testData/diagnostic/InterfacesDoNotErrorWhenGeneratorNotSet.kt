// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package test

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true, generator = "customGenerator") interface Interface
