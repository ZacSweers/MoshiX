@file:OptIn(ExperimentalStdlibApi::class)

package dev.zacsweers.moshix.ir.playground

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import org.junit.Test

class IrPlayground {

  @Test
  fun simple() {
    val moshi = Moshi.Builder().build()
    val adapter = moshi.adapter<SimpleClass>()
    val instance = adapter.fromJson("""{"a":3,"b":"there"}""")
    println(instance.toString())
    println(adapter.toJson(SimpleClass(3, "there")))
  }
}

@JsonClass(generateAdapter = true)
data class SimpleClass(val a: Int?, val b: String)
