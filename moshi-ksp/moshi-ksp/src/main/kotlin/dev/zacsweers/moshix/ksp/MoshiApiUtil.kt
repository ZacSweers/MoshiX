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
package dev.zacsweers.moshix.ksp

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asClassName
import com.squareup.moshi.JsonQualifier
import dev.zacsweers.moshix.ksp.shade.api.DelegateKey
import dev.zacsweers.moshix.ksp.shade.api.PropertyGenerator
import dev.zacsweers.moshix.ksp.shade.api.TargetProperty
import dev.zacsweers.moshix.ksp.shade.api.rawType

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
  val qualifiers = parameter?.qualifiers.orEmpty() + propertySpec.annotations
  for (jsonQualifier in qualifiers) {
    val qualifierRawType = jsonQualifier.typeName.rawType()
    // Check Java types since that covers both Java and Kotlin annotations.
    val annotationElement = resolver.getClassDeclarationByName(qualifierRawType.canonicalName)
    annotationElement.findAnnotationWithType<Retention>(resolver)?.let {
      // TODO this is super hacky but I don't know how else to compare enums here
      if (it.getMember<TypeName>("value") != AnnotationRetention::class.asClassName().nestedClass("RUNTIME")) {
        logger.error(
          "JsonQualifier @${qualifierRawType.simpleName} must have RUNTIME retention"
        )
      }
    }
    annotationElement.findAnnotationWithType<Target>(resolver)?.let {
      // TODO this is super hacky but I don't know how else to compare enums here
      if (AnnotationTarget::class.asClassName().nestedClass("FIELD") !in it.getMember<List<TypeName>>("allowedTargets")) {
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

internal fun KSClassDeclaration.isJsonQualifier(resolver: Resolver): Boolean {
  val jsonQualifier = resolver.getClassDeclarationByName<JsonQualifier>().asType()
  return hasAnnotation(jsonQualifier)
}
