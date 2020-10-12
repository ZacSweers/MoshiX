/*
 * Copyright (C) 2019 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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