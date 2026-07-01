// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package test

import com.squareup.moshi.JsonClass
import dev.zacsweers.moshix.sealed.annotations.FallbackJsonAdapter
import dev.zacsweers.moshix.sealed.annotations.TypeLabel
import dev.zacsweers.moshix.sealed.runtime.internal.ObjectJsonAdapter

<!MOSHI_ERROR!>@FallbackJsonAdapter(ObjectJsonAdapter::class)
@JsonClass(generateAdapter = true, generator = "sealed:type")
sealed class BaseType {
  @TypeLabel("a")
  class TypeA : BaseType()
}<!>

