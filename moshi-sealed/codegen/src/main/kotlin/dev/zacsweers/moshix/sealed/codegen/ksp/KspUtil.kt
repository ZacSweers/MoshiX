// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.sealed.codegen.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType

internal inline fun <reified T> Resolver.getClassDeclarationByName(): KSClassDeclaration {
  return getClassDeclarationByName(T::class.qualifiedName!!)
}

internal fun Resolver.getClassDeclarationByName(fqcn: String): KSClassDeclaration {
  return getClassDeclarationByName(getKSNameFromString(fqcn))
    ?: error("Class '$fqcn' not found on the classpath. Are you missing this dependency?")
}

internal fun KSClassDeclaration.asType() = asType(emptyList())

internal fun KSAnnotated.hasAnnotation(target: KSType): Boolean {
  return findAnnotationWithType(target) != null
}

internal fun KSAnnotated.findAnnotationWithType(target: KSType): KSAnnotation? {
  return annotations.find { it.annotationType.resolve() == target }
}

internal inline fun <reified T> KSAnnotation.getMember(name: String): T {
  val matchingArg =
    arguments.find { it.name?.asString() == name }
      ?: error(
        "No member name found for '$name'. All arguments: ${arguments.map { it.name?.asString() }}"
      )
  return matchingArg.value as? T ?: error("No value found for $name. Was ${matchingArg.value}")
}
