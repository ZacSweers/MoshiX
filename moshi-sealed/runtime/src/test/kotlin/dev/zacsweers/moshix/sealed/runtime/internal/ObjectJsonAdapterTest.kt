// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.sealed.runtime.internal

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.JsonDataException
import org.junit.Assert.fail
import org.junit.Test

class ObjectJsonAdapterTest {
  @Test
  fun basic() {
    val adapter = ObjectJsonAdapter(ObjectClass)
    assertThat(adapter.toJson(ObjectClass)).isEqualTo("{}")
    assertThat(adapter.fromJson("{}")).isSameInstanceAs(ObjectClass)
  }

  @Test
  fun withJsonContent() {
    val adapter = ObjectJsonAdapter(ObjectClass)
    assertThat(adapter.fromJson("{\"a\":6}")).isSameInstanceAs(ObjectClass)
  }

  @Test
  fun withJsonContent_failsOnUnknown() {
    val adapter = ObjectJsonAdapter(ObjectClass).failOnUnknown()
    try {
      adapter.fromJson("{\"a\":6}")
      fail("Should fail on unknown a name")
    } catch (e: JsonDataException) {
      assertThat(e).hasMessageThat().contains("Cannot skip unexpected NAME")
    }
  }

  object ObjectClass
}
