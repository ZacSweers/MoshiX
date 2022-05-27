package dev.zacsweers.moshi.sealed

import com.google.common.truth.Truth.assertThat
import dev.zacsweers.moshix.sealed.runtime.reflexiveSealedSubclasses
import org.junit.Test

class ReflexiveTest {
  sealed class Foo
  class Subtype : Foo()
  class Subtype2 : Foo()

  @Test
  fun doStuff() {
    val subtypes = Foo::class.reflexiveSealedSubclasses
    println(subtypes)
    assertThat(subtypes).containsExactly(Subtype::class, Subtype2::class)
  }
}