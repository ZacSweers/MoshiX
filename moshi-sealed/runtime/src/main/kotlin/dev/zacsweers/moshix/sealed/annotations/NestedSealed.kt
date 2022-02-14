/*
 * Copyright (C) 2020 Zac Sweers
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
