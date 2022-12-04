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
