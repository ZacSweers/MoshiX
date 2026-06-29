// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package com.squareup.moshi.kotlin.codegen

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import org.junit.Assume.assumeFalse
import org.junit.Test

class MixingReflectAndCodeGenTest {
  @Test
  fun mixingReflectionAndCodegen() {
    assumeFalse(System.getProperty("moshi.r8Test", "false").toBoolean())
    val kotlinJsonAdapter =
      Class.forName("com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory")
        .constructors
        .first()
        .newInstance() as JsonAdapter.Factory
    val moshi = Moshi.Builder().add(kotlinJsonAdapter).build()
    val generatedAdapter = moshi.adapter<UsesGeneratedAdapter>()
    val reflectionAdapter = moshi.adapter<UsesReflectionAdapter>()

    assertThat(generatedAdapter.toString())
      .isEqualTo(
        "GeneratedJsonAdapter(MixingReflectAndCodeGenTest.UsesGeneratedAdapter).nullSafe()"
      )
    assertThat(reflectionAdapter.toString())
      .isEqualTo(
        "KotlinJsonAdapter(com.squareup.moshi.kotlin.codegen.MixingReflectAndCodeGenTest" +
          ".UsesReflectionAdapter).nullSafe()"
      )
  }

  @JsonClass(generateAdapter = true) class UsesGeneratedAdapter(var a: Int, var b: Int)

  @JsonClass(generateAdapter = false) class UsesReflectionAdapter(var a: Int, var b: Int)
}
