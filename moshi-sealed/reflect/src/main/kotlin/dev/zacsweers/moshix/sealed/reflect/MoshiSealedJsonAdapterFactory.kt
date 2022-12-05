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
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import com.squareup.moshi.rawType
import dev.zacsweers.moshix.sealed.annotations.DefaultNull
import dev.zacsweers.moshix.sealed.annotations.DefaultObject
import dev.zacsweers.moshix.sealed.annotations.FallbackJsonAdapter
import dev.zacsweers.moshix.sealed.annotations.NestedSealed
import dev.zacsweers.moshix.sealed.annotations.TypeLabel
import dev.zacsweers.moshix.sealed.runtime.internal.ObjectJsonAdapter
import dev.zacsweers.moshix.sealed.runtime.internal.Util.fallbackAdapter
import java.lang.reflect.Type
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation

private val UNSET = Any()

public class MoshiSealedJsonAdapterFactory : JsonAdapter.Factory {
  override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
    if (annotations.isNotEmpty()) {
      return null
    }
    val rawType = type.rawType
    val rawTypeKotlin = rawType.kotlin

    val jsonClass = rawType.getAnnotation(JsonClass::class.java)
    if (jsonClass != null) {
      val labelKey = jsonClass.labelKey() ?: return null
      if (!rawTypeKotlin.isSealed) {
        return null
      }

      // If this is a nested sealed type of a moshi-sealed parent, defer to the parent
      if (rawType.getAnnotation(NestedSealed::class.java) != null) {
        val parentLabelKey =
          rawTypeKotlin.supertypes.firstNotNullOfOrNull { supertype ->
            // Weird that we need to check the classifier ourselves
            val nestedJsonClass =
              (supertype.classifier as? KClass<*>)?.findAnnotation<JsonClass>()
                ?: supertype.findAnnotation()
            nestedJsonClass?.labelKey()
          }
            ?: error("No JsonClass-annotated sealed supertype found for $rawTypeKotlin")
        check(parentLabelKey != labelKey) {
          "@NestedSealed-annotated subtype $rawType is inappropriately annotated with @JsonClass(generator = \"sealed:$labelKey\")."
        }
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
      var fallbackAdapter: JsonAdapter<Any>? = null
      val fallbackJsonAdapterAnnotation = rawType.getAnnotation(FallbackJsonAdapter::class.java)
      if (fallbackJsonAdapterAnnotation != null) {
        val clazz = fallbackJsonAdapterAnnotation.value
        // Find a constructor we can use
        fallbackAdapter = moshi.fallbackAdapter(clazz.java)
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
          } else if (defaultObjectInstance === UNSET && fallbackAdapter == null) {
            defaultObjectInstance = objectInstance
          } else {
            if (defaultObjectInstance == null || fallbackAdapter == null) {
              error(
                "Only one of @DefaultNull, @DefaultObject, and @FallbackJsonAdapter can be used at a time: $sealedSubclass"
              )
            } else {
              error(
                "Can only have one @DefaultObject: $sealedSubclass and ${defaultObjectInstance.javaClass} are both annotated"
              )
            }
          }
        } else {
          // For nested sealed classes, extract their labels and route to their adapter
          val clazz = sealedSubclass.java
          walkTypeLabels(sealedSubclass, labelKey, labels, objectSubtypes, clazz)

          check(clazz.typeParameters.isEmpty()) {
            "Moshi-sealed subtypes cannot be generic: $clazz"
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
            } else if (fallbackAdapter != null) {
              factory.withFallbackJsonAdapter(fallbackAdapter)
            } else {
              factory
            }
          }

      return polymorphicFactory.create(rawType, annotations, delegateMoshi)
    }

    return null
  }
}

private fun JsonClass.labelKey(): String? =
  if (generator.startsWith("sealed:")) {
    generator.removePrefix("sealed:")
  } else {
    null
  }

private fun walkTypeLabels(
  subtype: KClass<*>,
  labelKey: String,
  labels: MutableMap<String, Class<*>>,
  objectSubtypes: MutableMap<Class<*>, Any>,
  clazz: Class<*> = subtype.java,
) {
  // If it's sealed, check if it's inheriting from our existing type or a separate/new branching off
  // point.
  if (subtype.isSealed) {
    val nestedLabelKey = subtype.findAnnotation<JsonClass>()?.labelKey()
    if (nestedLabelKey != null) {
      // Redundant case
      if (labelKey == nestedLabelKey) {
        error(
          "Sealed subtype $subtype is redundantly annotated with @JsonClass(generator = " +
            "\"sealed:$nestedLabelKey\")."
        )
      }
    }

    if (subtype.hasAnnotation<TypeLabel>()) {
      // It's a different type, allow it to be used as a label and branch off from here.
      addLabelKeyForType(subtype, labels, objectSubtypes, skipJsonClassCheck = true)
    } else {
      // Recurse, inheriting the top type
      for (nested in subtype.sealedSubclasses) {
        walkTypeLabels(nested, labelKey, labels, objectSubtypes, clazz)
      }
    }
  } else {
    addLabelKeyForType(
      subtype,
      labels,
      objectSubtypes,
      skipJsonClassCheck = subtype.objectInstance != null
    )
  }
}

private fun addLabelKeyForType(
  subtype: KClass<*>,
  labels: MutableMap<String, Class<*>>,
  objectSubtypes: MutableMap<Class<*>, Any>,
  skipJsonClassCheck: Boolean = false
) {
  // Regular subtype, read its label
  val subtypeClazz = subtype.java
  val labelAnnotation =
    subtype.findAnnotation<TypeLabel>()
      ?: throw IllegalArgumentException(
        "Sealed subtypes must be annotated with @TypeLabel to define their label ${subtype.qualifiedName}"
      )
  val label = labelAnnotation.label
  labels.put(label, subtypeClazz)?.let { prev ->
    error("Duplicate label '$label' defined for $subtypeClazz and $prev.")
  }
  for (alternate in labelAnnotation.alternateLabels) {
    labels.put(alternate, subtypeClazz)?.let { prev ->
      error("Duplicate alternate label '$alternate' defined for $subtypeClazz and $prev.")
    }
  }
  subtype.objectInstance?.let { objectSubtypes[subtypeClazz] = it }
  check(skipJsonClassCheck || subtype.findAnnotation<JsonClass>()?.labelKey() == null) {
    "Sealed subtype $subtype is annotated with @JsonClass(generator = \"sealed:...\") and @TypeLabel."
  }
}
