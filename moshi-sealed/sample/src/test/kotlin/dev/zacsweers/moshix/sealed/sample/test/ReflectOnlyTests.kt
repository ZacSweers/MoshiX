package dev.zacsweers.moshix.sealed.sample.test

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.zacsweers.moshix.sealed.annotations.TypeLabel
import dev.zacsweers.moshix.sealed.reflect.MetadataMoshiSealedJsonAdapterFactory
import dev.zacsweers.moshix.sealed.reflect.MoshiSealedJsonAdapterFactory
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
@ExperimentalStdlibApi
class ReflectOnlyTests(type: Type) {

  enum class Type(val moshi: Moshi = Moshi.Builder().build()) {
    REFLECT(
      moshi = Moshi.Builder()
        .add(MoshiSealedJsonAdapterFactory())
        .addLast(KotlinJsonAdapterFactory())
        .build()
    ),
    METADATA_REFLECT(
      moshi = Moshi.Builder()
        .add(MetadataMoshiSealedJsonAdapterFactory())
        .addLast(KotlinJsonAdapterFactory())
        .build()
    )
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data(): Collection<Array<*>> {
      return listOf(
        arrayOf(Type.REFLECT),
        arrayOf(Type.METADATA_REFLECT)
      )
    }
  }

  private val moshi: Moshi = type.moshi

  @Test
  fun duplicateLabels() {
    try {
      moshi.adapter<DuplicateLabels>()
      fail()
    } catch (e: IllegalStateException) {
      assertThat(e).hasMessageThat().contains("Duplicate label")
    }
  }

  @JsonClass(generateAdapter = false, generator = "sealed:type")
  sealed class DuplicateLabels {
    @TypeLabel("a")
    class TypeA : DuplicateLabels()

    @TypeLabel("a")
    class TypeB : DuplicateLabels()
  }

  @Test
  fun duplicateAlternateLabels() {
    try {
      moshi.adapter<DuplicateAlternateLabels>()
      fail()
    } catch (e: IllegalStateException) {
      assertThat(e).hasMessageThat().contains("Duplicate alternate label")
    }
  }

  @JsonClass(generateAdapter = false, generator = "sealed:type")
  sealed class DuplicateAlternateLabels {
    @TypeLabel("a", alternateLabels = ["aa"])
    class TypeA : DuplicateAlternateLabels()

    @TypeLabel("b", alternateLabels = ["aa"])
    class TypeB : DuplicateAlternateLabels()
  }
}