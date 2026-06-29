// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.adapters

import com.google.common.truth.Truth.assertThat
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
    val moshi =
      Builder()
        .add(TrackUnknownKeys.Factory(tracker = tracker))
        .addLast(KotlinJsonAdapterFactory())
        .build()

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
