package dev.zacsweers.moshix.adapters.immutable

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentCollection
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Test

class ImmutableCollectionsJsonAdapterFactoryTest {
  private val moshi =
    Moshi.Builder()
      .add(ImmutableCollectionsJsonAdapterFactory())
      .addLast(KotlinJsonAdapterFactory())
      .build()

  // language=JSON
  private val json =
    """
      {
        "list": [1, 2, 3],
        "set": [1, 2, 3],
        "collection": [1, 2, 3],
        "map": {
          "a": 1,
          "b": 2,
          "c": 3
        },
        "nested": {
          "a": [1, 2, 3],
          "b": [1, 2, 3],
          "c": [1, 2, 3]
        },
        "persistentList": [1, 2, 3],
        "persistentSet": [1, 2, 3],
        "persistentCollection": [1, 2, 3],
        "persistentMap": {
          "a": 1,
          "b": 2,
          "c": 3
        },
        "persistentNested": {
          "a": [1, 2, 3],
          "b": [1, 2, 3],
          "c": [1, 2, 3]
        }
      }
        """
      .trimIndent()

  @Test
  fun smokeTest() {
    val adapter = moshi.adapter<ClassWithImmutables>()
    val instance = adapter.fromJson(json)!!
    val expectedInstance =
      ClassWithImmutables(
        list = persistentListOf(1, 2, 3),
        set = persistentSetOf(1, 2, 3),
        collection = persistentListOf(1, 2, 3),
        map = persistentMapOf("a" to 1, "b" to 2, "c" to 3),
        nested =
          persistentMapOf(
            "a" to persistentListOf(1, 2, 3),
            "b" to persistentListOf(1, 2, 3),
            "c" to persistentListOf(1, 2, 3),
          ),
        persistentList = persistentListOf(1, 2, 3),
        persistentSet = persistentSetOf(1, 2, 3),
        persistentCollection = persistentListOf(1, 2, 3),
        persistentMap = persistentMapOf("a" to 1, "b" to 2, "c" to 3),
        persistentNested =
          persistentMapOf(
            "a" to persistentListOf(1, 2, 3),
            "b" to persistentListOf(1, 2, 3),
            "c" to persistentListOf(1, 2, 3),
          ),
      )
    assertThat(instance).isEqualTo(expectedInstance)
    val serializedAndReserializedInstance = adapter.fromJson(adapter.toJson(expectedInstance))!!
    assertThat(serializedAndReserializedInstance).isEqualTo(expectedInstance)
  }

  data class ClassWithImmutables(
    val list: ImmutableList<Int>,
    val set: ImmutableSet<Int>,
    val collection: ImmutableCollection<Int>,
    val map: ImmutableMap<String, Int>,
    val nested: ImmutableMap<String, ImmutableList<Int>>,
    val persistentList: PersistentList<Int>,
    val persistentSet: PersistentSet<Int>,
    val persistentCollection: PersistentCollection<Int>,
    val persistentMap: PersistentMap<String, Int>,
    val persistentNested: PersistentMap<String, PersistentList<Int>>,
  )
}
