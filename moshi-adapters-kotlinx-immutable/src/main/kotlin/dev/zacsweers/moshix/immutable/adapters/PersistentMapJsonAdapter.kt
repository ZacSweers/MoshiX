package dev.zacsweers.moshix.immutable.adapters

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.rawType
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Applying [PersistentMapJsonAdapterFactory] to your Moshi instance will allow serialization between
 * JSON dictionary and [kotlinx.collections.immutable.PersistentMap] types.
 *
 * Example:
 * ```
 * JSON:
 * {
 *   "stringMap": {
 *     "a": "1",
 *     "b": "2",
 *     "c": "3"
 *   },
 *   "objMap": {
 *     "a": {"value": "test1"},
 *     "b": {"value": "test2"},
 *     "c": {"value": "test3"}
 *   }
 * }
 *
 * val moshi = Moshi.Builder()
 *   .add(PersistentMapJsonAdapterFactory)
 *   .build()
 *
 * @JsonClass(generateAdapter = true)
 * data class Data(
 *   val strings: PersistentMap<String, String>,
 *   val people: PersistentMap<String, SimpleValueObject>
 * )
 *
 * @JsonClass(generateAdapter = true)
 * private data class SimpleValueObject(val value: String)
 * ```
 */
public object PersistentMapJsonAdapterFactory : JsonAdapter.Factory {

    override fun create(type: Type, annotations: MutableSet<out Annotation>, moshi: Moshi): JsonAdapter<*>? {
        if (!PersistentMap::class.java.isAssignableFrom(type.rawType)
            || type !is ParameterizedType
            || annotations.isNotEmpty()
            || type.actualTypeArguments.size != 2
        ) {
            return null
        }

        val keyType = type.actualTypeArguments[0]
        val valueType = type.actualTypeArguments[1]

        val keyAdapter = moshi.adapter(keyType.rawType)
        val valueAdapter = moshi.adapter(valueType.rawType)

        return PersistentMapJsonAdapter(
            keyAdapter = keyAdapter,
            valueAdapter = valueAdapter,
        ).nullSafe()
    }
}

private class PersistentMapJsonAdapter<K, V>(
    private val keyAdapter: JsonAdapter<K>,
    private val valueAdapter: JsonAdapter<V>,
) : JsonAdapter<PersistentMap<K, V?>>() {

    override fun fromJson(reader: JsonReader): PersistentMap<K, V?> {
        val result = persistentMapOf<K, V?>().builder()
        reader.beginObject()
        while (reader.hasNext()) {
            reader.promoteNameToValue()
            val name = keyAdapter.fromJson(reader)
            val value = valueAdapter.fromJson(reader)
            if (name != null) {
                result[name] = value
            }
        }
        reader.endObject()
        return result.build()
    }

    override fun toJson(writer: JsonWriter, map: PersistentMap<K, V?>?) {
        writer.beginObject()
        map?.forEach { (key, value) ->
            writer.promoteValueToName()
            keyAdapter.toJson(writer, key)
            valueAdapter.toJson(writer, value)
        }
        writer.endObject()
    }
}
