package dev.zacsweers.moshi.codegen

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.asClassName
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.kotlin.codegen.api.DelegateKey
import com.squareup.moshi.kotlin.codegen.api.PropertyGenerator
import com.squareup.moshi.kotlin.codegen.api.TargetProperty
import com.squareup.moshi.kotlin.codegen.api.rawType
import dev.zacsweers.moshisealed.codegen.asType
import dev.zacsweers.moshisealed.codegen.findAnnotationWithType
import dev.zacsweers.moshisealed.codegen.getClassDeclarationByName
import dev.zacsweers.moshisealed.codegen.getMember
import dev.zacsweers.moshisealed.codegen.hasAnnotation
import org.jetbrains.kotlin.ksp.processing.KSPLogger
import org.jetbrains.kotlin.ksp.processing.Resolver
import org.jetbrains.kotlin.ksp.symbol.KSClassDeclaration
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

private val VISIBILITY_MODIFIERS = setOf(
  KModifier.INTERNAL,
  KModifier.PRIVATE,
  KModifier.PROTECTED,
  KModifier.PUBLIC
)

internal fun Collection<KModifier>.visibility(): KModifier {
  return find { it in VISIBILITY_MODIFIERS } ?: KModifier.PUBLIC
}

private val TargetProperty.isTransient get() = propertySpec.annotations.any { it.typeName == Transient::class.asClassName() }
private val TargetProperty.isSettable get() = propertySpec.mutable || parameter != null
private val TargetProperty.isVisible: Boolean
  get() {
    return visibility == KModifier.INTERNAL ||
      visibility == KModifier.PROTECTED ||
      visibility == KModifier.PUBLIC
  }

/**
 * Returns a generator for this property, or null if either there is an error and this property
 * cannot be used with code gen, or if no codegen is necessary for this property.
 */
internal fun TargetProperty.generator(
  logger: KSPLogger,
  resolver: Resolver,
  originalType: KSClassDeclaration
): PropertyGenerator? {
  if (isTransient) {
    if (!hasDefault) {
      logger.error(
        "No default value for transient property $name",
        originalType
      )
      return null
    }
    return PropertyGenerator(this, DelegateKey(type, emptyList()), true)
  }

  if (!isVisible) {
    logger.error(
      "property $name is not visible",
      originalType
    )
    return null
  }

  if (!isSettable) {
    return null // This property is not settable. Ignore it.
  }

  // Merge parameter and property annotations
  val qualifiers = parameter?.qualifiers.orEmpty() + propertySpec.annotations.qualifiers(resolver)
  for (jsonQualifier in qualifiers) {
    val qualifierRawType = jsonQualifier.typeName.rawType()
    // Check Java types since that covers both Java and Kotlin annotations.
    val annotationElement = resolver.getClassDeclarationByName(qualifierRawType.canonicalName)
    annotationElement.findAnnotationWithType<Retention>(resolver)?.let {
      if (it.getMember<RetentionPolicy>("value") != RetentionPolicy.RUNTIME) {
        logger.error(
          "JsonQualifier @${qualifierRawType.simpleName} must have RUNTIME retention"
        )
      }
    }
    annotationElement.findAnnotationWithType<Target>(resolver)?.let {
      if (ElementType.FIELD !in it.getMember<Array<ElementType>>("value")) {
        logger.error(
          "JsonQualifier @${qualifierRawType.simpleName} must support FIELD target"
        )
      }
    }
  }

  val jsonQualifierSpecs = qualifiers.map {
    it.toBuilder()
      .useSiteTarget(AnnotationSpec.UseSiteTarget.FIELD)
      .build()
  }

  return PropertyGenerator(
    this,
    DelegateKey(type, jsonQualifierSpecs)
  )
}

private fun List<AnnotationSpec>?.qualifiers(resolver: Resolver): Set<AnnotationSpec> {
  if (this == null) return setOf()
  val jsonQualifier = resolver.getClassDeclarationByName<JsonQualifier>().asType()
  return filterTo(mutableSetOf()) {
    resolver.getClassDeclarationByName(it.typeName.toString()).hasAnnotation(jsonQualifier)
  }
}