package dev.zacsweers.moshix.adapters.immutable

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentCollection
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * A [JsonAdapter.Factory] that creates immutable collection adapters for the following supported
 * types:
 * - [ImmutableCollection]
 * - [ImmutableList]
 * - [ImmutableSet]
 * - [ImmutableMap]
 * - [PersistentCollection]
 * - [PersistentList]
 * - [PersistentSet]
 * - [PersistentMap]
 */
public class ImmutableCollectionsJsonAdapterFactory : JsonAdapter.Factory {
  override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
    if (type !is ParameterizedType) return null
    when (type.rawType) {
      ImmutableList::class.java,
      ImmutableCollection::class.java,
      PersistentList::class.java,
      PersistentCollection::class.java -> {
        val elementType = type.actualTypeArguments[0]
        val elementAdapter = moshi.adapter<Any>(elementType, annotations)
        return ImmutableListAdapter(elementAdapter)
      }
      ImmutableSet::class.java,
      PersistentSet::class.java -> {
        val elementType = type.actualTypeArguments[0]
        val elementAdapter = moshi.adapter<Any>(elementType, annotations)
        return ImmutableSetAdapter(elementAdapter)
      }
      ImmutableMap::class.java,
      PersistentMap::class.java -> {
        val keyType = type.actualTypeArguments[0]
        val valueType = type.actualTypeArguments[1]
        val keyAdapter = moshi.adapter<Any>(keyType, annotations)
        val valueAdapter = moshi.adapter<Any>(valueType, annotations)
        return ImmutableMapAdapter(keyAdapter, valueAdapter)
      }
      else -> return null
    }
  }
}

private sealed class ImmutableCollectionAdapter<C : ImmutableCollection<E>, E>(
  private val elementAdapter: JsonAdapter<E>
) : JsonAdapter<C>() {

  abstract fun buildCollection(body: (MutableCollection<E>) -> Unit): C

  override fun fromJson(reader: JsonReader): C? {
    reader.beginArray()
    val collection = buildCollection { builder ->
      while (reader.hasNext()) {
        builder += elementAdapter.fromJson(reader) ?: error("Null element at ${reader.path}")
      }
    }
    reader.endArray()
    return collection
  }

  override fun toJson(writer: JsonWriter, value: C?) {
    if (value == null) {
      writer.nullValue()
    } else {
      writer.beginArray()
      for (element in value) {
        elementAdapter.toJson(writer, element)
      }
      writer.endArray()
    }
  }
}

private class ImmutableListAdapter<E>(elementAdapter: JsonAdapter<E>) :
  ImmutableCollectionAdapter<ImmutableList<E>, E>(elementAdapter) {
  override fun buildCollection(body: (MutableCollection<E>) -> Unit) =
    persistentListOf<E>().mutate(body)
}

private class ImmutableSetAdapter<E>(elementAdapter: JsonAdapter<E>) :
  ImmutableCollectionAdapter<ImmutableSet<E>, E>(elementAdapter) {
  override fun buildCollection(body: (MutableCollection<E>) -> Unit) =
    persistentSetOf<E>().mutate(body)
}

private class ImmutableMapAdapter<K, V>(
  private val keyAdapter: JsonAdapter<K>,
  private val valueAdapter: JsonAdapter<V>,
) : JsonAdapter<ImmutableMap<K, V>>() {

  override fun fromJson(reader: JsonReader): ImmutableMap<K, V> {
    reader.beginObject()
    return persistentMapOf<K, V>()
      .mutate {
        while (reader.hasNext()) {
          reader.promoteNameToValue()
          val key = keyAdapter.fromJson(reader) ?: error("Null key at ${reader.path}")
          val value = valueAdapter.fromJson(reader) ?: error("Null value at ${reader.path}")
          val replaced = it.put(key, value)
          if (replaced != null) {
            throw JsonDataException(
              "Duplicate element '$key' with value '$replaced' at ${reader.path}"
            )
          }
        }
      }
      .also { reader.endObject() }
  }

  override fun toJson(writer: JsonWriter, value: ImmutableMap<K, V>?) {
    if (value == null) {
      writer.nullValue()
    } else {
      writer.beginObject()
      for ((k, v) in value) {
        writer.promoteValueToName()
        keyAdapter.toJson(writer, k)
        valueAdapter.toJson(writer, v)
      }
      writer.endObject()
    }
  }
}
