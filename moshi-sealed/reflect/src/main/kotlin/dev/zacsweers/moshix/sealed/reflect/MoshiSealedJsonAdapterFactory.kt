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
package dev.zacsweers.moshix.sealed.reflect

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import dev.zacsweers.moshix.sealed.annotations.DefaultNull
import dev.zacsweers.moshix.sealed.annotations.DefaultObject
import dev.zacsweers.moshix.sealed.annotations.TypeLabel
import dev.zacsweers.moshix.sealed.runtime.internal.ObjectJsonAdapter
import java.lang.reflect.Type
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

private val UNSET = Any()

public class MoshiSealedJsonAdapterFactory : JsonAdapter.Factory {
  override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
    if (annotations.isNotEmpty()) {
      return null
    }
    val rawType = Types.getRawType(type)
    val rawTypeKotlin = rawType.kotlin
    rawType.getAnnotation(JsonClass::class.java)?.let { jsonClass ->
      val generator = jsonClass.generator
      if (!generator.startsWith("sealed:")) {
        return null
      }
      val labelKey = generator.removePrefix("sealed:")
      if (!rawTypeKotlin.isSealed) {
        return null
      }

      // Pull out the default instance as necessary
      // Possible cases:
      //   - No default (error if missing at runtime)
      //   - Null default
      //   - Object default
      var defaultObjectInstance: Any? = UNSET
      if (rawTypeKotlin.annotations.any { it is DefaultNull }) {
        defaultObjectInstance = null
      }

      val objectSubtypes = mutableMapOf<Class<*>, Any>()
      val labels = mutableMapOf<String, Class<*>>()

      for (sealedSubclass in rawTypeKotlin.sealedSubclasses) {
        val objectInstance = sealedSubclass.objectInstance
        val isAnnotatedDefaultObject =
            sealedSubclass.java.isAnnotationPresent(DefaultObject::class.java)
        if (isAnnotatedDefaultObject) {
          if (objectInstance == null) {
            error("Must be an object type to use as a @DefaultObject: $sealedSubclass")
          } else if (defaultObjectInstance === UNSET) {
            defaultObjectInstance = objectInstance
          } else {
            if (defaultObjectInstance == null) {
              error("Can not have both @DefaultObject and @DefaultNull: $sealedSubclass")
            } else {
              error(
                  "Can only have one @DefaultObject: $sealedSubclass and ${defaultObjectInstance.javaClass} are both annotated")
            }
          }
        } else {
          // For nested sealed classes, extract their labels and route to their adapter
          val clazz = sealedSubclass.java
          walkTypeLabels(sealedSubclass, labelKey, labels, clazz)

          check(clazz.typeParameters.isEmpty()) {
            "Moshi-sealed subtypes cannot be generic: $clazz"
          }

          if (objectInstance != null) {
            objectSubtypes[sealedSubclass.java] = objectInstance
          }
        }
      }

      val delegateMoshi =
          if (objectSubtypes.isEmpty()) {
            moshi
          } else {
            moshi
                .newBuilder()
                .apply {
                  for ((subtype, instance) in objectSubtypes) {
                    add(subtype, ObjectJsonAdapter(instance))
                  }
                }
                .build()
          }
      @Suppress("UNCHECKED_CAST")
      val seed = PolymorphicJsonAdapterFactory.of(rawType as Class<Any>?, labelKey)
      val polymorphicFactory =
          labels.entries
              .fold(seed) { factory, (label, subtype) -> factory.withSubtype(subtype, label) }
              .let { factory ->
                if (defaultObjectInstance !== UNSET) {
                  factory.withDefaultValue(defaultObjectInstance)
                } else {
                  factory
                }
              }

      return polymorphicFactory.create(rawType, annotations, delegateMoshi)
    }
    return null
  }
}

private fun walkTypeLabels(
    subtype: KClass<*>,
    labelKey: String,
    labels: MutableMap<String, Class<*>>,
    clazz: Class<*> = subtype.java,
) {
  // If it's sealed, check if it's inheriting from our existing type or a separate/new branching off
  // point
  if (subtype.isSealed) {
    val jsonClass = subtype.findAnnotation<JsonClass>()
    if (jsonClass != null && jsonClass.generator.startsWith("sealed:")) {
      val sealedTypeDiscriminator = jsonClass.generator.removePrefix("sealed:")
      // Redundant case
      if (labelKey == sealedTypeDiscriminator) {
        error(
            "Sealed subtype $subtype is redundantly annotated with @JsonClass(generator = " +
                "\"sealed:$sealedTypeDiscriminator\").")
      } else {
        // It's a different type, allow it to be used as a label
        addLabelKeyForType(subtype, labels, skipJsonClassCheck = true)
      }
    } else {
      // Recurse, inheriting the top type
      for (nested in subtype.sealedSubclasses) {
        walkTypeLabels(nested, labelKey, labels, clazz)
      }
    }
  } else {
    addLabelKeyForType(subtype, labels)
  }
}

private fun addLabelKeyForType(
    subtype: KClass<*>,
    labels: MutableMap<String, Class<*>>,
    skipJsonClassCheck: Boolean = false
) {
  // Regular subtype, read its label
  val subtypeClazz = subtype.java
  val labelAnnotation =
      subtype.findAnnotation<TypeLabel>()
          ?: throw IllegalArgumentException(
              "Sealed subtypes must be annotated with @TypeLabel to define their label ${subtype.qualifiedName}")
  val label = labelAnnotation.label
  labels.put(label, subtypeClazz)?.let { prev ->
    error("Duplicate label '$label' defined for $subtypeClazz and $prev.")
  }
  for (alternate in labelAnnotation.alternateLabels) {
    labels.put(alternate, subtypeClazz)?.let { prev ->
      error("Duplicate alternate label '$alternate' defined for $subtypeClazz and $prev.")
    }
  }
  check(
      skipJsonClassCheck ||
          subtype.findAnnotation<JsonClass>()?.generator?.startsWith("sealed:") == false) {
    "Sealed subtype $subtype is annotated with @JsonClass(generator = \"sealed:...\") and @TypeLabel."
  }
}
