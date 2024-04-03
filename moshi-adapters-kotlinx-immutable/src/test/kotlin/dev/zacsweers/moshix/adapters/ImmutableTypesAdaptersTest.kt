package dev.zacsweers.moshix.adapters

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.zacsweers.moshix.immutable.adapters.addKotlinXImmutableAdapters
import kotlinx.collections.immutable.PersistentCollection
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Test

class ImmutableTypesAdaptersTest {

    // Will be deserialized into a `PersistentList<String>`
    private val testListOfStringsJson = """
        {
            "stringList": ["a", "b", "c"]
        }
    """.trimIndent()

    // Will be deserialized into a `PersistentList<SimpleObject>`
    private val testListOfObjectsJson = """
        {
            "objList": [
                {"value": "test1"},
                {"value": "test2"}
            ]
        }
    """.trimIndent()

    // Will be deserialized into a `PersistentMap<String, String>`
    private val testMapOfStringsJson = """
        {
            "stringMap": {
                "a": "1",
                "b": "2",
                "c": "3"
            }
        }
    """.trimIndent()

    // Will be deserialized into a `PersistentMap<String, SimpleObject>`
    private val testMapOfObjectsJson = """
        {
            "objMap": {
                "a": {"value": "test1"},
                "b": {"value": "test2"},
                "c": {"value": "test3"}
            }
        }
    """.trimIndent()

    private val moshi = Moshi.Builder()
        .addKotlinXImmutableAdapters()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun `test immutable type deserialization for PersistentList of Strings`() {
        val adapter = moshi.adapter(ListOfStringsType::class.java)
        val response = adapter.fromJson(testListOfStringsJson)
        assertThat(response?.stringList)
            .isEqualTo(persistentListOf("a", "b", "c"))
    }

    @Test
    fun `test immutable type deserialization for PersistentList of Objects`() {
        val adapter = moshi.adapter(ListOfObjectsType::class.java)
        val response = adapter.fromJson(testListOfObjectsJson)
        assertThat(response?.objList)
            .isEqualTo(
                persistentListOf(
                    SimpleObject("test1"),
                    SimpleObject("test2"),
                )
            )
    }

    @Test
    fun `test immutable type deserialization for PersistentSet of Strings`() {
        val adapter = moshi.adapter(SetOfStringsType::class.java)
        val response = adapter.fromJson(testListOfStringsJson)
        assertThat(response?.stringList)
            .isEqualTo(persistentSetOf("a", "b", "c"))
    }

    @Test
    fun `test immutable type deserialization for PersistentSet of Objects`() {
        val adapter = moshi.adapter(SetOfObjectsType::class.java)
        val response = adapter.fromJson(testListOfObjectsJson)
        assertThat(response?.objList)
            .isEqualTo(
                persistentSetOf(
                    SimpleObject("test1"),
                    SimpleObject("test2"),
                )
            )
    }

    @Test
    fun `test immutable type deserialization for PersistentCollection of Strings`() {
        val adapter = moshi.adapter(CollectionOfStringsType::class.java)
        val response = adapter.fromJson(testListOfStringsJson)
        assertThat(response?.stringList)
            .containsExactly("a", "b", "c")
    }

    @Test
    fun `test immutable type deserialization for PersistentCollection of Objects`() {
        val adapter = moshi.adapter(CollectionOfObjectsType::class.java)
        val response = adapter.fromJson(testListOfObjectsJson)
        assertThat(response?.objList)
            .containsExactly(SimpleObject("test1"), SimpleObject("test2"))
    }

    @Test
    fun `test immutable type deserialization for PersistentMap of String to String`() {
        val adapter = moshi.adapter(MapOfStringsType::class.java)
        val response = adapter.fromJson(testMapOfStringsJson)
        assertThat(response?.stringMap)
            .isEqualTo(persistentMapOf("a" to "1", "b" to "2", "c" to "3"))
    }

    @Test
    fun `test immutable type deserialization for PersistentMap of String to Object`() {
        val adapter = moshi.adapter(MapOfObjectsType::class.java)
        val response = adapter.fromJson(testMapOfObjectsJson)
        assertThat(response?.objMap)
            .isEqualTo(
                persistentMapOf(
                    "a" to SimpleObject("test1"),
                    "b" to SimpleObject("test2"),
                    "c" to SimpleObject("test3"),
                )
            )
    }
}

@JsonClass(generateAdapter = true)
internal data class ListOfStringsType(
    val stringList: PersistentList<String>,
)

@JsonClass(generateAdapter = true)
internal data class ListOfObjectsType(
    val objList: PersistentList<SimpleObject>,
)

@JsonClass(generateAdapter = true)
internal data class SetOfStringsType(
    val stringList: PersistentSet<String>,
)

@JsonClass(generateAdapter = true)
internal data class SetOfObjectsType(
    val objList: PersistentSet<SimpleObject>,
)

@JsonClass(generateAdapter = true)
internal data class CollectionOfStringsType(
    val stringList: PersistentCollection<String>,
)

@JsonClass(generateAdapter = true)
internal data class CollectionOfObjectsType(
    val objList: PersistentCollection<SimpleObject>,
)

@JsonClass(generateAdapter = true)
internal data class MapOfStringsType(
    val stringMap: PersistentMap<String, String>,
)

@JsonClass(generateAdapter = true)
internal data class MapOfObjectsType(
    val objMap: PersistentMap<String, SimpleObject>,
)

@JsonClass(generateAdapter = true)
internal data class SimpleObject(
    val value: String
)
