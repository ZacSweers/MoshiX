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
import kotlin.reflect.full.findAnnotation

private val UNSET = Any()

public class MoshiSealedJsonAdapterFactory : JsonAdapter.Factory {
  override fun create(type: Type,
    annotations: MutableSet<out Annotation>,
    moshi: Moshi): JsonAdapter<*>? {
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
      val typeLabel = generator.removePrefix("sealed:")
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
        val isAnnotatedDefaultObject = sealedSubclass.java.isAnnotationPresent(DefaultObject::class.java)
        if (isAnnotatedDefaultObject) {
          if (objectInstance == null) {
            error("Must be an object type to use as a @DefaultObject: $sealedSubclass")
          } else if (defaultObjectInstance === UNSET) {
            defaultObjectInstance = objectInstance
          } else {
            if (defaultObjectInstance == null) {
              error("Can not have both @DefaultObject and @DefaultNull: $sealedSubclass")
            } else {
              error("Can only have one @DefaultObject: $sealedSubclass and ${defaultObjectInstance.javaClass} are both annotated")
            }
          }
        } else {
          val labelAnnotation = sealedSubclass.findAnnotation<TypeLabel>()
            ?: throw IllegalArgumentException(
              "Sealed subtypes must be annotated with @TypeLabel to define their label ${sealedSubclass.qualifiedName}")
          val clazz = sealedSubclass.java
          val label = labelAnnotation.label
          labels[label] = clazz
          for (alternate in labelAnnotation.alternateLabels) {
            labels[alternate] = clazz
          }
          if (objectInstance != null) {
            objectSubtypes[sealedSubclass.java] = objectInstance
          }
        }
      }

      val delegateMoshi = if (objectSubtypes.isEmpty()) {
        moshi
      } else {
        moshi.newBuilder()
          .apply {
            for ((subtype, instance) in objectSubtypes) {
              add(subtype, ObjectJsonAdapter(instance))
            }
          }
          .build()
      }
      @Suppress("UNCHECKED_CAST")
      val seed = PolymorphicJsonAdapterFactory.of(rawType as Class<Any>?, typeLabel)
      val polymorphicFactory = labels.entries
        .fold(seed) { factory, (label, subtype) ->
          factory.withSubtype(subtype, label)
        }
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
