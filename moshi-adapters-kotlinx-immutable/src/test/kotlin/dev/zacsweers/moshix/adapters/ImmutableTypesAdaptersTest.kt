package dev.zacsweers.moshix.adapters

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.zacsweers.moshix.immutable.adapters.ImmutableCollectionJsonAdapterFactory
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

class ImmutableTypesAdaptersTest {

    // Will be deserialized into a `ImmutableList<SimpleObject>` or `PersistentList<SimpleObject>`
    private val testListOfObjectsJson = """
        {
            "objList": [
                {"value": "test1"},
                {"value": "test2"}
            ]
        }
    """.trimIndent()

    // Will be deserialized into a `ImmutableMap<String, SimpleObject>` or `PersistentMap<String, SimpleObject>`
    private val testMapOfObjectsJson = """
        {
            "objMap": {
                "a": {"value": "test1"},
                "b": {"value": "test2"}
            }
        }
    """.trimIndent()

    private val object1 = SimpleObject("test1")
    private val object2 = SimpleObject("test2")

    private val moshi = Moshi.Builder()
        .add(ImmutableCollectionJsonAdapterFactory())
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun `test deserialization PersistentList of Objects`() {
        val adapter = moshi.adapter(PersistentListOfObjectsType::class.java)
        val response = adapter.fromJson(testListOfObjectsJson)
        assertThat(response?.objList)
            .isEqualTo(persistentListOf(object1, object2))
    }

    @Test
    fun `test deserialization ImmutableList of Objects`() {
        val adapter = moshi.adapter(ImmutableListOfObjectsType::class.java)
        val response = adapter.fromJson(testListOfObjectsJson)
        assertThat(response?.objList)
            .isEqualTo(persistentListOf(object1, object2))
    }

    @Test
    fun `test deserialization PersistentSet of Objects`() {
        val adapter = moshi.adapter(PersistentSetOfObjectsType::class.java)
        val response = adapter.fromJson(testListOfObjectsJson)
        assertThat(response?.objList)
            .isEqualTo(persistentSetOf(object1, object2))
    }

    @Test
    fun `test deserialization ImmutableSet of Objects`() {
        val adapter = moshi.adapter(ImmutableSetOfObjectsType::class.java)
        val response = adapter.fromJson(testListOfObjectsJson)
        assertThat(response?.objList)
            .isEqualTo(persistentSetOf(object1, object2))
    }

    @Test
    fun `test deserialization PersistentCollection of Objects`() {
        val adapter = moshi.adapter(PersistentCollectionOfObjectsType::class.java)
        val response = adapter.fromJson(testListOfObjectsJson)
        assertThat(response?.objList)
            .containsExactly(object1, object2)
    }

    @Test
    fun `test deserialization ImmutableCollection of Objects`() {
        val adapter = moshi.adapter(ImmutableCollectionOfObjectsType::class.java)
        val response = adapter.fromJson(testListOfObjectsJson)
        assertThat(response?.objList)
            .containsExactly(object1, object2)
    }

    @Test
    fun `test deserialization PersistentMap of String to Object`() {
        val adapter = moshi.adapter(PersistentMapOfObjectsType::class.java)
        val response = adapter.fromJson(testMapOfObjectsJson)
        assertThat(response?.objMap)
            .isEqualTo(
                persistentMapOf(
                    "a" to object1,
                    "b" to object2,
                )
            )
    }

    @Test
    fun `test deserialization ImmutableMap of String to Object`() {
        val adapter = moshi.adapter(ImmutableMapOfObjectsType::class.java)
        val response = adapter.fromJson(testMapOfObjectsJson)
        assertThat(response?.objMap)
            .isEqualTo(
                persistentMapOf(
                    "a" to object1,
                    "b" to object2,
                )
            )
    }
}

@JsonClass(generateAdapter = true)
internal data class PersistentListOfObjectsType(
    val objList: PersistentList<SimpleObject>,
)

@JsonClass(generateAdapter = true)
internal data class ImmutableListOfObjectsType(
    val objList: ImmutableList<SimpleObject>,
)

@JsonClass(generateAdapter = true)
internal data class PersistentSetOfObjectsType(
    val objList: PersistentSet<SimpleObject>,
)

@JsonClass(generateAdapter = true)
internal data class ImmutableSetOfObjectsType(
    val objList: ImmutableSet<SimpleObject>,
)

@JsonClass(generateAdapter = true)
internal data class PersistentCollectionOfObjectsType(
    val objList: PersistentCollection<SimpleObject>,
)

@JsonClass(generateAdapter = true)
internal data class ImmutableCollectionOfObjectsType(
    val objList: ImmutableCollection<SimpleObject>,
)

@JsonClass(generateAdapter = true)
internal data class PersistentMapOfObjectsType(
    val objMap: PersistentMap<String, SimpleObject>,
)

@JsonClass(generateAdapter = true)
internal data class ImmutableMapOfObjectsType(
    val objMap: ImmutableMap<String, SimpleObject>,
)

@JsonClass(generateAdapter = true)
internal data class SimpleObject(
    val value: String
)
