package dev.zacsweers.moshix.sealed.reflect

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import dev.zacsweers.moshix.sealed.annotations.DefaultNull
import dev.zacsweers.moshix.sealed.annotations.DefaultObject
import dev.zacsweers.moshix.sealed.annotations.TypeLabel
import java.lang.reflect.Type
import kotlin.reflect.full.findAnnotation

private object UNSET

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
      } else {
        rawTypeKotlin.sealedSubclasses
            .firstOrNull { subclass -> subclass.annotations.any { it is DefaultObject } }
            ?.let {
              it.objectInstance ?: error("Must be an object type to use as a @DefaultObject")
            }
            ?.run {
              rawTypeKotlin.sealedSubclasses.asSequence()
                  .mapNotNull { it.objectInstance }
                  .singleOrNull()
            }
            ?.let {
              defaultObjectInstance = it
            }
      }

      @Suppress("UNCHECKED_CAST")
      val polymorphicFactory = PolymorphicJsonAdapterFactory.of(rawType as Class<Any>?, typeLabel)
          .let {
            if (defaultObjectInstance != UNSET) {
              it.withDefaultValue(defaultObjectInstance)
            } else {
              it
            }
          }
          .let {
            rawTypeKotlin.sealedSubclasses
                .filter { subclass -> subclass.objectInstance == null }
                .fold(it) { factory, sealedSubclass ->
              val label = sealedSubclass.findAnnotation<TypeLabel>()?.value
                  ?: throw IllegalArgumentException("Sealed subtypes must be annotated with @TypeLabel to define their label ${sealedSubclass.qualifiedName}")
              factory.withSubtype(sealedSubclass.java, label)
            }
          }

      return polymorphicFactory.create(rawType, annotations, moshi)
    }
    return null
  }
}
