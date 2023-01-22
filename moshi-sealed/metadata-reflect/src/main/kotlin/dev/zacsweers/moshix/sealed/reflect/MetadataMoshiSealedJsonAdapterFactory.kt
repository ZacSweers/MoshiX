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
import kotlinx.metadata.ClassName
import kotlinx.metadata.Flag
import kotlinx.metadata.KmClass
import kotlinx.metadata.jvm.KotlinClassMetadata
import kotlinx.metadata.jvm.Metadata

/** Classes annotated with this are eligible for this adapter. */
private val KOTLIN_METADATA = Metadata::class.java

private val UNSET = Any()

public class MetadataMoshiSealedJsonAdapterFactory : JsonAdapter.Factory {
  override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
    if (annotations.isNotEmpty()) {
      return null
    }
    val rawType = type.rawType
    if (!rawType.isAnnotationPresent(KOTLIN_METADATA)) return null

    rawType.getAnnotation(JsonClass::class.java)?.let { jsonClass ->
      val labelKey = jsonClass.labelKey() ?: return null
      val kmClass = checkNotNull(rawType.header()?.toKmClass())

      if (!Flag.IS_SEALED(kmClass.flags)) {
        return null
      }

      // If this is a nested sealed type of a moshi-sealed parent, defer to the parent
      if (rawType.getAnnotation(NestedSealed::class.java) != null) {
        val supertypes: List<Class<*>> = listOfNotNull(rawType.superclass, *rawType.interfaces)
        val parentLabelKey =
          supertypes.firstNotNullOfOrNull { supertype ->
            supertype.getAnnotation(JsonClass::class.java)?.labelKey()
          }
            ?: error("No JsonClass-annotated sealed supertype found for $rawType")
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
      if (rawType.isAnnotationPresent(DefaultNull::class.java)) {
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
      for (sealedSubclassName in kmClass.sealedSubclasses) {
        val sealedSubclass = sealedSubclassName.toJavaClass()
        val kmSealedSubclass = checkNotNull(sealedSubclass.header()?.toKmClass())
        val isObject = Flag.Class.IS_OBJECT(kmSealedSubclass.flags)

        val isAnnotatedDefaultObject = sealedSubclass.isAnnotationPresent(DefaultObject::class.java)
        if (isAnnotatedDefaultObject) {
          if (!isObject) {
            error("Must be an object type to use as a @DefaultObject: $sealedSubclass")
          } else if (defaultObjectInstance === UNSET && fallbackAdapter == null) {
            defaultObjectInstance = sealedSubclass.objectInstance()
          } else {
            if (defaultObjectInstance == null || fallbackAdapter == null) {
              error(
                "Only one of @DefaultNull, @DefaultObject, and @FallbackJsonAdapter: $sealedSubclass"
              )
            } else {
              error(
                "Can only have one @DefaultObject: $sealedSubclass and ${defaultObjectInstance.javaClass} are both annotated"
              )
            }
          }
        } else {
          walkTypeLabels(sealedSubclass, labelKey, labels, objectSubtypes)
          check(sealedSubclass.typeParameters.isEmpty()) {
            "Moshi-sealed subtypes cannot be generic: $sealedSubclass"
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

private fun Class<*>.header(): Metadata? {
  val metadata = getAnnotation(KOTLIN_METADATA) ?: return null
  return with(metadata) {
    Metadata(
      kind = kind,
      metadataVersion = metadataVersion,
      data1 = data1,
      data2 = data2,
      extraString = extraString,
      packageName = packageName,
      extraInt = extraInt
    )
  }
}

private fun Metadata.toKmClass(): KmClass? {
  val classMetadata = KotlinClassMetadata.read(this)
  if (classMetadata !is KotlinClassMetadata.Class) {
    return null
  }
  return classMetadata.toKmClass()
}

private fun JsonClass.labelKey(): String? =
  if (generator.startsWith("sealed:")) {
    generator.removePrefix("sealed:")
  } else {
    null
  }

private fun ClassName.toJavaClass(): Class<*> {
  return Class.forName(replace(".", "$").replace("/", "."))
}

private fun Class<*>.objectInstance(): Any {
  return getDeclaredField("INSTANCE").get(null)
}

private fun walkTypeLabels(
  subtype: Class<*>,
  labelKey: String,
  labels: MutableMap<String, Class<*>>,
  objectSubtypes: MutableMap<Class<*>, Any>,
) {
  // If it's sealed, check if it's inheriting from our existing type or a separate/new branching off
  // point
  val subtypeKmClass =
    subtype.header()?.toKmClass()
      ?: error("Cannot decode Metadata for $subtype. Is it not a Kotlin class?")
  if (Flag.IS_SEALED(subtypeKmClass.flags)) {
    val jsonClass = subtype.getAnnotation(JsonClass::class.java)
    if (jsonClass != null && jsonClass.generator.startsWith("sealed:")) {
      val sealedTypeDiscriminator = jsonClass.generator.removePrefix("sealed:")
      // Redundant case
      if (labelKey == sealedTypeDiscriminator) {
        error(
          "Sealed subtype $subtype is redundantly annotated with @JsonClass(generator = " +
            "\"sealed:$sealedTypeDiscriminator\")."
        )
      }
    }

    if (subtype.isAnnotationPresent(TypeLabel::class.java)) {
      // It's a different type, allow it to be used as a label and branch off from here.
      addLabelKeyForType(subtype, subtypeKmClass, labels, objectSubtypes, skipJsonClassCheck = true)
    } else {
      // Recurse, inheriting the top type
      for (nested in subtypeKmClass.sealedSubclasses.map { it.toJavaClass() }) {
        walkTypeLabels(nested, labelKey, labels, objectSubtypes)
      }
    }
  } else {
    addLabelKeyForType(
      subtype,
      subtypeKmClass,
      labels,
      objectSubtypes,
      skipJsonClassCheck = Flag.Class.IS_OBJECT(subtypeKmClass.flags)
    )
  }
}

private fun addLabelKeyForType(
  sealedSubclass: Class<*>,
  subtypeKmClass: KmClass,
  labels: MutableMap<String, Class<*>>,
  objectSubtypes: MutableMap<Class<*>, Any>,
  skipJsonClassCheck: Boolean = false
) {
  // Regular subtype, read its label
  val labelAnnotation =
    checkNotNull(sealedSubclass.getAnnotation(TypeLabel::class.java)) {
      "Sealed subtypes must be annotated with @TypeLabel to define their label $sealedSubclass"
    }

  val label = labelAnnotation.label
  labels.put(label, sealedSubclass)?.let { prev ->
    error("Duplicate label '$label' defined for $sealedSubclass and $prev.")
  }
  for (alternate in labelAnnotation.alternateLabels) {
    labels.put(alternate, sealedSubclass)?.let { prev ->
      error("Duplicate alternate label '$alternate' defined for $sealedSubclass and $prev.")
    }
  }
  if (Flag.Class.IS_OBJECT(subtypeKmClass.flags)) {
    objectSubtypes[sealedSubclass] = sealedSubclass.objectInstance()
  }
  check(
    skipJsonClassCheck || sealedSubclass.getAnnotation(JsonClass::class.java)?.labelKey() == null
  ) {
    "Sealed subtype $sealedSubclass is annotated with @JsonClass(generator = \"sealed:...\") and @TypeLabel."
  }
}
