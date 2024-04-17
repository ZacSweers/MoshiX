package dev.zacsweers.moshix.immutable.adapters

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.rawType
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
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Applying [ImmutableCollectionsJsonAdapterFactory] to your Moshi instance will allow serialization between
 * JSON lists/map and these types:
 *  - [kotlinx.collections.immutable.PersistentCollection]
 *  - [kotlinx.collections.immutable.PersistentList]
 *  - [kotlinx.collections.immutable.PersistentMap]
 *  - [kotlinx.collections.immutable.PersistentSet]
 *  - [kotlinx.collections.immutable.ImmutableCollection]
 *  - [kotlinx.collections.immutable.ImmutableList]
 *  - [kotlinx.collections.immutable.ImmutableMap]
 *  - [kotlinx.collections.immutable.ImmutableSet]
 *
 * Example:
 * ```
 * val moshi = Moshi.Builder()
 *   .add(ImmutableCollectionsJsonAdapterFactory())
 *   .build()
 *
 * @JsonClass(generateAdapter = true)
 * data class Data(
 *   val stringList: ImmutableList<String>, // or PersistentList<String>,
 *   val objList: ImmutableList<SomeObject>, // or PersistentList<SomeObject>,
 *   val objCollection: ImmutableCollection<SomeObject>, // or PersistentCollection<SomeObject>,
 *   val objMap: ImmutableMap<String, SomeObject>, // or PersistentMap<String, SomeObject>,
 *   val objectsSet: ImmutableMap<SomeObject>, // or PersistentMap<SomeObject>,
 * )
 * ```
 */

public class ImmutableCollectionsJsonAdapterFactory : JsonAdapter.Factory {

    override fun create(type: Type, annotations: MutableSet<out Annotation>, moshi: Moshi): JsonAdapter<*>? {
        if (type !is ParameterizedType) return null
        if (annotations.isNotEmpty()) return null

        return when (type.rawType) {
            ImmutableList::class.java, ImmutableCollection::class.java,
            PersistentList::class.java, PersistentCollection::class.java ->
                newListAdapter<Any>(type, moshi).nullSafe()

            ImmutableSet::class.java, PersistentSet::class.java ->
                newSetAdapter<Any>(type, moshi).nullSafe()

            ImmutableMap::class.java, PersistentMap::class.java ->
                newMapAdapter(type, moshi).nullSafe()

            else -> null
        }
    }

    private fun <T> newListAdapter(type: ParameterizedType, moshi: Moshi): JsonAdapter<PersistentCollection<T?>> {
        val elementType = Types.collectionElementType(type, PersistentCollection::class.java)
        val elementAdapter = moshi.adapter<T>(elementType)
        return object : ImmutableCollectionJsonAdapter<PersistentCollection<T?>, T>(elementAdapter) {
            override fun newCollection(): PersistentCollection<T?> = persistentListOf()
        }
    }

    private fun <T> newSetAdapter(type: ParameterizedType, moshi: Moshi): JsonAdapter<PersistentSet<T?>> {
        val elementType = Types.collectionElementType(type, PersistentSet::class.java)
        val elementAdapter = moshi.adapter<T>(elementType)
        return object : ImmutableCollectionJsonAdapter<PersistentSet<T?>, T>(elementAdapter) {
            override fun newCollection(): PersistentSet<T?> = persistentSetOf()
        }
    }

    private fun newMapAdapter(type: ParameterizedType, moshi: Moshi): JsonAdapter<out PersistentMap<out Any, Any?>> {
        // alternative of Types.mapKeyAndValueTypes(type, type.rawType)
        val keyType = type.actualTypeArguments[0]
        val valueType = type.actualTypeArguments[1]

        val keyAdapter = moshi.adapter(keyType.rawType)
        val valueAdapter = moshi.adapter(valueType.rawType)

        return ImmutableMapJsonAdapter(
            keyAdapter = keyAdapter,
            valueAdapter = valueAdapter,
        ).nullSafe()
    }
}

private abstract class ImmutableCollectionJsonAdapter<E : PersistentCollection<T?>, T>(
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
        requireNotNull(value) // Always wrapped in nullSafe()
        writer.beginArray()
        value.forEach { adapter.toJson(writer, it) }
        writer.endArray()
    }
}

private class ImmutableMapJsonAdapter<K, V>(
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
        requireNotNull(map) // Always wrapped in nullSafe()
        writer.beginObject()
        map.forEach { (key, value) ->
            writer.promoteValueToName()
            keyAdapter.toJson(writer, key)
            valueAdapter.toJson(writer, value)
        }
        writer.endObject()
    }
}