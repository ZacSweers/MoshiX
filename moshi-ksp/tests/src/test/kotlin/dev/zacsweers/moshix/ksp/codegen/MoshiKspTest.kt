package dev.zacsweers.moshix.ksp.codegen

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import org.junit.Test

// Tests specific to Moshi-KSP
class MoshiKspTest {
  private val moshi = Moshi.Builder().build()

  // Regression test for https://github.com/ZacSweers/MoshiX/issues/44
  @Test
  fun onlyInterfaceSupertypes() {
    val adapter = moshi.adapter<SimpleImpl>()
    //language=JSON
    val json = """{"a":"aValue","b":"bValue"}"""
    val expected = SimpleImpl("aValue", "bValue")
    val instance = adapter.fromJson(json)!!
    assertThat(instance).isEqualTo(expected)
    val encoded = adapter.toJson(instance)
    assertThat(encoded).isEqualTo(json)
  }

  interface SimpleInterface{
    val a : String
  }

  // NOTE the Any() superclass is important to test that we're detecting the farthest parent class
  // correct.y
  @JsonClass(generateAdapter = true)
  data class SimpleImpl(override val a : String, val b : String) : Any(), SimpleInterface
}

