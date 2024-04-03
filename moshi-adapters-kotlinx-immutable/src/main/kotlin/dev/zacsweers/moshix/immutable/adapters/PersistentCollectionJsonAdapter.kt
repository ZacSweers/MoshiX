package dev.zacsweers.moshix.immutable.adapters

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.rawType
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Applying [PersistentListJsonAdapterFactory] to your Moshi instance will allow serialization between
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
public object PersistentListJsonAdapterFactory : JsonAdapter.Factory {

    override fun create(type: Type, annotations: MutableSet<out Annotation>, moshi: Moshi): JsonAdapter<*>? {
        if (!PersistentList::class.java.isAssignableFrom(type.rawType) || type !is ParameterizedType) {
            return null
        }
        val adapter = moshi.adapter(type.actualTypeArguments[0].rawType)
        return PersistentListJsonAdapter(adapter).nullSafe()
    }
}

private class PersistentListJsonAdapter<E>(private val adapter: JsonAdapter<E>) : JsonAdapter<PersistentList<E>>() {

    override fun fromJson(reader: JsonReader): PersistentList<E> {
        val listBuilder = persistentListOf<E>().builder()
        reader.beginArray()
        while (reader.hasNext()) {
            listBuilder.add(
                adapter.fromJson(reader) ?: error("[PersistentListJsonAdapter] Error parsing item: $reader")
            )
        }
        reader.endArray()
        return listBuilder.build()
    }

    override fun toJson(writer: JsonWriter, value: PersistentList<E>?) {
        writer.beginArray()
        value?.forEach { adapter.toJson(writer, it) }
        writer.endArray()
    }
}