package dev.zacsweers.moshix.sealed.annotations

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.rawType
import java.lang.reflect.Type

// TODO doc
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class Payload(val typeKey: String) {
  public object Factory : JsonAdapter.Factory {
    override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
      // Look for the PayloadType annotation
      val payload = type.rawType.getAnnotation(Payload::class.java)?.typeKey ?: return null
      val delegate = moshi.nextAdapter<Any>(this, type, annotations)
      return PayloadJsonAdapter(payload, delegate)
    }
  }
}

private class PayloadJsonAdapter<T>(
  typeKey: String,
  private val delegate: JsonAdapter<T>
): JsonAdapter<T>() {
  private val options = JsonReader.Options.of(typeKey)

  override fun fromJson(reader: JsonReader): T? {
    // Peek ahead, find the type
    val peeked = reader.peekJson()
    peeked.setFailOnUnknown(false)
    val type = peeked.use(::parseType)
    // Store the hint in the reader for layer reading
    reader.setTag(PayloadTypeHint::class.java, PayloadTypeHint(type))
    return delegate.fromJson(reader)
  }

  private fun parseType(reader: JsonReader): String? {
    reader.beginObject()
    while (reader.hasNext()) {
      if (reader.selectName(options) == -1) {
        reader.skipName()
        reader.skipValue()
        continue
      }
      // Would be nice if we could selectString() here instead, but hard to do without code gen
      return reader.nextString()
    }
    return null
  }

  override fun toJson(writer: JsonWriter, value: T?) {
    delegate.toJson(writer, value)
  }
}

internal class PayloadTypeHint(val type: String?)
