package dev.zacsweers.moshix.immutable.adapters

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.collections.immutable.PersistentCollection
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Applying [PersistentCollectionJsonAdapterFactory] to your Moshi instance will allow serialization between
 * JSON lists and [kotlinx.collections.immutable.PersistentList] types.
 *
 * Example:
 * ```
 * JSON:
 * {
 *   "strings": ["value1", "value2", "value3"],
 *   "people": [
 *     {"first": "Jane", "last": "Doe"},
 *     {"first": "John", "last": "Doh"}
 *   ]
 * }
 *
 * val moshi = Moshi.Builder()
 *   .add(PersistentListJsonAdapterFactory)
 *   .build()
 *
 * @JsonClass(generateAdapter = true)
 * data class Data(
 *   val strings: PersistentList<String>,
 *   val people: PersistentList<Person>
 * )
 * ```
 */
public object PersistentCollectionJsonAdapterFactory : JsonAdapter.Factory {

    override fun create(type: Type, annotations: MutableSet<out Annotation>, moshi: Moshi): JsonAdapter<*>? {
        if (type !is ParameterizedType) return null
        if (annotations.isNotEmpty()) return null

        return when (type.rawType) {
            PersistentList::class.java ->
                newListAdapter<Any>(type, moshi).nullSafe()

            PersistentSet::class.java ->
                newSetAdapter<Any>(type, moshi).nullSafe()

            else -> null
        }
    }

    private fun <T> newListAdapter(type: ParameterizedType, moshi: Moshi): JsonAdapter<PersistentCollection<T?>> {
        val elementType = Types.collectionElementType(type, PersistentCollection::class.java)
        val elementAdapter = moshi.adapter<T>(elementType)
        return object : PersistentCollectionJsonAdapter<PersistentCollection<T?>, T>(elementAdapter) {
            override fun newCollection(): PersistentCollection<T?> = persistentListOf()
        }
    }

    private fun <T> newSetAdapter(type: ParameterizedType, moshi: Moshi): JsonAdapter<PersistentSet<T?>> {
        val elementType = Types.collectionElementType(type, PersistentSet::class.java)
        val elementAdapter = moshi.adapter<T>(elementType)
        return object : PersistentCollectionJsonAdapter<PersistentSet<T?>, T>(elementAdapter) {
            override fun newCollection(): PersistentSet<T?> = persistentSetOf()
        }
    }
}

private abstract class PersistentCollectionJsonAdapter<E : PersistentCollection<T?>, T>(
    private val adapter: JsonAdapter<T>
) : JsonAdapter<E>() {

    abstract fun newCollection(): E

    override fun fromJson(reader: JsonReader): E {
        val listBuilder = newCollection().builder()
        reader.beginArray()
        while (reader.hasNext()) {
            listBuilder.add(
                adapter.fromJson(reader) ?: error("[PersistentListJsonAdapter] Error parsing item: $reader")
            )
        }
        reader.endArray()
        return listBuilder.build() as E
    }

    override fun toJson(writer: JsonWriter, value: E?) {
        writer.beginArray()
        value?.forEach { adapter.toJson(writer, it) }
        writer.endArray()
    }
}