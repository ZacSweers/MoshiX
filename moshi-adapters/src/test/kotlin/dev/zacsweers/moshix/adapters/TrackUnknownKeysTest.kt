/*
 * Copyright (C) 2021 Zac Sweers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.zacsweers.moshix.adapters

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi.Builder
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Test

class TrackUnknownKeysTest {

  @Test
  fun simpleCase() {
    // language=JSON
    val json = "{\"a\":1,\"b\":2,\"c\":3}"

    val tracker = RecordingKeysTracker()
    val moshi = Builder().add(TrackUnknownKeys.Factory(tracker = tracker)).addLast(KotlinJsonAdapterFactory()).build()

    val example = moshi.adapter<Letters>().fromJson(json)!!
    assertThat(example).isEqualTo(Letters(1, 2))
    tracker.assertUnknown<Letters>("c")
  }

  @TrackUnknownKeys data class Letters(val a: Int, val b: Int)

  class RecordingKeysTracker : TrackUnknownKeys.UnknownKeysTracker {

    val recorded = mutableMapOf<Class<*>, List<String>>()

    override fun track(clazz: Class<*>, unknownKeys: List<String>) {
      recorded[clazz] = unknownKeys
    }

    inline fun <reified T> assertUnknown(vararg expected: String) {
      val clazz = T::class.java
      val keys = recorded[clazz] ?: error("No keys found for $clazz")
      assertThat(keys).containsExactly(*expected).inOrder()
    }
  }
}
