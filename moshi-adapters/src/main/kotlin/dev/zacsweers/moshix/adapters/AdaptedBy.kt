/*
 * Copyright (C) 2014 Zac Sweers
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
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.Moshi
import com.squareup.moshi.rawType
import java.lang.reflect.Type
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FIELD
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.reflect.KClass

/**
 * An annotation that indicates the Moshi [JsonAdapter] or [JsonAdapter.Factory] to use with a class
 * or property. The adapter class must have a public default constructor.
 *
 * Here is an example of how this annotation is used:
 * ```
 * @AdaptedBy(UserJsonAdapter::class)
 * class User(val firstNam: String, val lastName: String)
 *
 * class UserJsonAdapter : JsonAdapter<User>() {
 *   override fun toJson(writer: JsonWriter, user: User) {
 *     // implement write: combine firstName and lastName into name
 *     writer.beginObject()
 *     writer.name("name")
 *     writer.value(user.firstName + " " + user.lastName)
 *     writer.endObject()
 *     // implement the toJson method
 *   }
 *   override fun User fromJson(JsonReader reader) {
 *     // implement read: split name into firstName and lastName
 *     reader.beginObject()
 *     reader.nextName()
 *     val nameParts = reader.nextString().split(" ")
 *     reader.endObject()
 *     return User(nameParts[0], nameParts[1])
 *   }
 * }
 * ```
 *
 * Since `User` class specified `UserJsonAdapter` in `@AdaptedBy` annotation, it will be invoked to
 * encode/decode `User` instances.
 *
 * Here is an example of how to apply this annotation to a property as a [JsonQualifier].
 *
 * ```
 * class Gadget(
 *   @AdaptedBy(UserJsonAdapter2::class) val user: User
 * )
 * ```
 *
 * The class referenced by this annotation must be either a [JsonAdapter] or a [JsonAdapter.Factory]
 * . Using [JsonAdapter.Factory] makes it possible to delegate to the enclosing [Moshi] instance.
 *
 * @property adapter Either a [JsonAdapter] or [JsonAdapter.Factory].
 * @property nullSafe Set to false to be able to handle null values within the adapter, default
 *   value is true.
 */
@JsonQualifier
@Retention(RUNTIME)
@Target(CLASS, PROPERTY, FIELD)
public annotation class AdaptedBy(val adapter: KClass<*>, val nullSafe: Boolean = true) {
  public class Factory : JsonAdapter.Factory {
    override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
      var adaptedBy: AdaptedBy?
      var nextAnnotations: MutableSet<Annotation>? = null
      val rawType = type.rawType
      adaptedBy = rawType.getAnnotation(AdaptedBy::class.java)
      if (adaptedBy == null) {
        for (annotation in annotations) {
          if (annotation is AdaptedBy) {
            adaptedBy = annotation
          } else {
            if (nextAnnotations == null) {
              nextAnnotations = mutableSetOf()
            }
            nextAnnotations.add(annotation)
          }
        }
      }

      if (adaptedBy == null) return null
      val adapterClass = adaptedBy.adapter
      val javaClass = adapterClass.java
      val adapter =
        when {
          JsonAdapter.Factory::class.java.isAssignableFrom(javaClass) -> {
            val factory = javaClass.getDeclaredConstructor().newInstance() as JsonAdapter.Factory
            factory.create(type, nextAnnotations.orEmpty(), moshi)
          }
          JsonAdapter::class.java.isAssignableFrom(javaClass) -> {
            javaClass.getDeclaredConstructor().newInstance() as JsonAdapter<*>
          }
          else -> {
            error(
              "Invalid attempt to bind an instance of ${javaClass.name} as a @AdaptedBy for $type. @AdaptedBy " +
                "value must be a JsonAdapter or JsonAdapter.Factory."
            )
          }
        }
          ?: return null

      return if (adaptedBy.nullSafe) {
        adapter.nullSafe()
      } else {
        adapter
      }
    }
  }
}
