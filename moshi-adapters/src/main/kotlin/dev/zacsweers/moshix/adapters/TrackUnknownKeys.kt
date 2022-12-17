/*
 * Copyright (C) 2021 Zac Sweers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.zacsweers.moshix.adapters

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonReader.Token
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.nextAnnotations
import java.lang.reflect.Type
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY
import okio.Buffer
import okio.blackholeSink
import okio.buffer

/**
 * An annotation to help track unknown keys in JSON.
 *
 * Note that this adapter is slow because it must parse the entire JSON object ahead of time, then
 * writes and re-reads it.
 *
 * In general, it is not recommended to use this adapter in production unless absolutely necessary
 * or to sample its usage. This should be used for debugging/logging information only.
 *
 * Usage:
 * ```
 * val moshi = Moshi.Builder()
 *   .add(TrackUnknownKeys.Factory())
 *   .build()
 *
 * @TrackUnknownKeys
 * @JsonClass(generateAdapter = true)
 * data class Message(
 *   val data: String
 * )
 *
 * // JSON of {"data": "value", "foo": "bar"} would report an unknown "foo"
 * ```
 */
@JsonQualifier
@Retention(RUNTIME)
@Target(CLASS, PROPERTY, FUNCTION)
public annotation class TrackUnknownKeys {

  public fun interface UnknownKeysTracker {
    public fun track(clazz: Class<*>, unknownKeys: List<String>)
  }

  /**
   * @param shouldTrack a function to compute if a given class and annotation set should be tracked.
   *   Defaults to
   *
   * ```
   *                    checking for the [TrackUnknownKeys] annotation.
   * @param tracker
   * ```
   *
   * a callback function for tracking unknown names for a given class.
   */
  public class Factory(
    private val shouldTrack: (clazz: Class<*>, annotations: Set<Annotation>) -> Boolean = { _, _ ->
      true
    },
    private val tracker: UnknownKeysTracker
  ) : JsonAdapter.Factory {
    override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
      if (type !is Class<*>) return null
      val nextAnnotations = annotations.nextAnnotations<TrackUnknownKeys>()
      if (nextAnnotations == null && !type.isAnnotationPresent(TrackUnknownKeys::class.java))
        return null
      if (!shouldTrack(type, annotations)) return null
      val delegate = moshi.nextAdapter<Any>(this, type, nextAnnotations ?: annotations)
      return TrackUnknownKeysJsonAdapter(delegate, type, tracker)
    }

    private class TrackUnknownKeysJsonAdapter<T>(
      private val delegate: JsonAdapter<T>,
      private val clazz: Class<*>,
      private val tracker: UnknownKeysTracker
    ) : JsonAdapter<T>() {
      override fun fromJson(reader: JsonReader): T? {
        val token = reader.peek()
        if (token != Token.BEGIN_OBJECT) {
          throw JsonDataException("Expected BEGIN_OBJECT but was $token at path ${reader.path}")
        }

        // Here's where we get clever.
        // Read the keys from the original JSON
        val writer = JsonWriter.of(blackholeSink().buffer()).apply { serializeNulls = true }
        // TODO delegating JsonReader to pass through and get instance in the same way?
        val originalKeys = writer.use { reader.peekJson().readAndGetKeys(it) }

        // TODO Use this someday instead: https://github.com/square/moshi/issues/1296

        // Parse a new instance and then write it back out to a buffer
        // The serialized buffer will only contain keys (including for missing keys)
        val instance = delegate.fromJson(reader)
        val buffer = Buffer()
        delegate.toJson(JsonWriter.of(buffer).apply { serializeNulls = false }, instance)

        // Parse the buffer
        val parsed = JsonReader.of(buffer).readAndGetKeys(JsonWriter.of(blackholeSink().buffer()))
        val unknownKeys = originalKeys.filterNot { it in parsed }
        if (unknownKeys.isNotEmpty()) {
          tracker.track(clazz, unknownKeys)
        }
        return instance
      }

      override fun toJson(writer: JsonWriter, value: T?) = delegate.toJson(writer, value)
    }
  }
}

/** A pass-through reader that records seen names. */
private fun JsonReader.readAndGetKeys(writer: JsonWriter): Set<String> {
  val keys = mutableSetOf<String>()
  beginObject()
  writer.beginObject()
  while (hasNext()) {
    val nextName = nextName()
    keys += nextName
    writer.name(nextName)
    readTo(writer)
  }
  writer.endObject()
  endObject()
  return keys
}

/** Streams the contents of a given Moshi [reader] into this writer. */
private fun JsonReader.readTo(writer: JsonWriter) {
  when (val token = peek()) {
    Token.BEGIN_ARRAY -> {
      beginArray()
      writer.beginArray()
      while (hasNext()) {
        readTo(writer)
      }
      writer.endArray()
      endArray()
    }
    Token.BEGIN_OBJECT -> {
      beginObject()
      writer.beginObject()
      while (hasNext()) {
        writer.name(nextName())
        readTo(writer)
      }
      writer.endObject()
      endObject()
    }
    Token.STRING -> writer.value(nextString())
    Token.NUMBER -> {
      // This allows us to preserve encoding from the reader,
      // avoiding issues like Moshi's `toJsonValue` API converting all
      // numbers potentially to Doubles.
      val lenient = isLenient
      isLenient = true
      try {
        writer.jsonValue(nextString())
      } finally {
        isLenient = lenient
      }
    }
    Token.BOOLEAN -> writer.value(nextBoolean())
    Token.NULL -> writer.value(nextNull<String>())
    Token.NAME,
    Token.END_ARRAY,
    Token.END_OBJECT,
    Token.END_DOCUMENT -> {
      throw JsonDataException("Unexpected token $token at $path")
    }
  }
}
