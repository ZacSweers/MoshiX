// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.sealed.annotations

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.rawType
import java.lang.reflect.Type
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS

/**
 * Use this annotation to specify that a given sealed type is a subtype of another sealed type and
 * uses the same key as the supertype.
 *
 * The supertype must be a _direct_ supertype. It is an error to annotate this type with [JsonClass]
 * as this supersedes that annotation.
 */
@Target(CLASS)
@Retention(RUNTIME)
public annotation class NestedSealed {
  /**
   * Optional factory that can be installed to enable lookup of nested sealed types directly from
   * [Moshi.adapter] calls.
   */
  public class Factory : JsonAdapter.Factory {
    override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
      if (annotations.isNotEmpty()) return null
      val rawType = type.rawType
      return if (rawType.getAnnotation(NestedSealed::class.java) != null) {
        (sequenceOf(rawType.superclass) + rawType.interfaces)
          .filterNotNull()
          .firstOrNull {
            it.getAnnotation(JsonClass::class.java)?.generator?.startsWith("sealed:") == true
          }
          ?.let { moshi.adapter(it) }
      } else {
        null
      }
    }
  }
}
