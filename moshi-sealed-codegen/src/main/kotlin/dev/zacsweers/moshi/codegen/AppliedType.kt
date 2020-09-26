/*
 * Copyright (C) 2018 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.zacsweers.moshi.codegen

import dev.zacsweers.moshisealed.codegen.toTypeName
import org.jetbrains.kotlin.ksp.getAllSuperTypes
import org.jetbrains.kotlin.ksp.processing.Resolver
import org.jetbrains.kotlin.ksp.symbol.KSClassDeclaration

/**
 * A concrete type like `List<String>` with enough information to know how to resolve its type
 * variables.
 */
internal class AppliedType private constructor(val type: KSClassDeclaration) {
  val typeName = type.toTypeName()

  /** Returns all supertypes of this, recursively. Includes both interface and class supertypes. */
  fun supertypes(
      resolver: Resolver,
      result: LinkedHashSet<AppliedType> = LinkedHashSet()
  ): LinkedHashSet<AppliedType> {
    result.add(this)
    for (supertype in type.getAllSuperTypes()) {
      check(supertype.declaration is KSClassDeclaration)
      val superTypeKsClass = resolver.getClassDeclarationByName(
          supertype.declaration.qualifiedName!!)!!
      val appliedSupertype = AppliedType(superTypeKsClass)
      result.add(appliedSupertype)
    }
    return result
  }

  override fun toString() = type.qualifiedName!!.asString()

  companion object {
    fun get(type: KSClassDeclaration): AppliedType {
      return AppliedType(type)
    }
  }
}
