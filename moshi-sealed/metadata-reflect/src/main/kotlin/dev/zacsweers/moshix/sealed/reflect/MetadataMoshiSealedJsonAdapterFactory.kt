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
import kotlinx.metadata.ClassName
import kotlinx.metadata.Flag
import kotlinx.metadata.KmClass
import kotlinx.metadata.jvm.KotlinClassHeader
import kotlinx.metadata.jvm.KotlinClassMetadata

/** Classes annotated with this are eligible for this adapter. */
private val KOTLIN_METADATA = Metadata::class.java

private val UNSET = Any()

public class MetadataMoshiSealedJsonAdapterFactory : JsonAdapter.Factory {
  override fun create(
      type: Type,
      annotations: MutableSet<out Annotation>,
      moshi: Moshi
  ): JsonAdapter<*>? {
    if (annotations.isNotEmpty()) {
      return null
    }
    val rawType = Types.getRawType(type)
    if (!rawType.isAnnotationPresent(KOTLIN_METADATA)) return null

    rawType.getAnnotation(JsonClass::class.java)?.let { jsonClass ->
      val generator = jsonClass.generator
      if (!generator.startsWith("sealed:")) {
        return null
      }

      val kmClass = checkNotNull(rawType.header()?.toKmClass())

      val typeLabel = generator.removePrefix("sealed:")
      if (!Flag.IS_SEALED(kmClass.flags)) {
        return null
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
          } else if (defaultObjectInstance === UNSET) {
            defaultObjectInstance = sealedSubclass.objectInstance()
          } else {
            if (defaultObjectInstance == null) {
              error("Can not have both @DefaultObject and @DefaultNull: $sealedSubclass")
            } else {
              error(
                  "Can only have one @DefaultObject: $sealedSubclass and ${defaultObjectInstance.javaClass} are both annotated")
            }
          }
        } else {
          val labelAnnotation =
              checkNotNull(sealedSubclass.getAnnotation(TypeLabel::class.java)) {
                "Sealed subtypes must be annotated with @TypeLabel to define their label $sealedSubclass"
              }

          check(sealedSubclass.typeParameters.isEmpty()) {
            "Moshi-sealed subtypes cannot be generic: $sealedSubclass"
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
          if (isObject) {
            objectSubtypes[sealedSubclass] = sealedSubclass.objectInstance()
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
      val seed = PolymorphicJsonAdapterFactory.of(rawType as Class<Any>?, typeLabel)
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

private fun Class<*>.header(): KotlinClassHeader? {
  val metadata = getAnnotation(KOTLIN_METADATA) ?: return null
  return with(metadata) {
    KotlinClassHeader(
        kind = kind,
        metadataVersion = metadataVersion,
        data1 = data1,
        data2 = data2,
        extraString = extraString,
        packageName = packageName,
        extraInt = extraInt)
  }
}

private fun KotlinClassHeader.toKmClass(): KmClass? {
  val classMetadata = KotlinClassMetadata.read(this)
  if (classMetadata !is KotlinClassMetadata.Class) {
    return null
  }
  return classMetadata.toKmClass()
}

private fun ClassName.toJavaClass(): Class<*> {
  return Class.forName(replace(".", "$").replace("/", "."))
}

private fun Class<*>.objectInstance(): Any {
  return getDeclaredField("INSTANCE").get(null)
}
