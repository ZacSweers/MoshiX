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
package dev.zacsweers.moshix.ir.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.getAllSuperclasses

/**
 * A concrete type like `List<String>` with enough information to know how to resolve its type
 * variables.
 */
internal class AppliedType private constructor(val type: IrClass) {

  /**
   * Returns all super classes of this, recursively. Only [IrClass] is used as we can't really use
   * other types.
   */
  fun superclasses(pluginContext: IrPluginContext): LinkedHashSet<AppliedType> {
    val result: LinkedHashSet<AppliedType> = LinkedHashSet()
    result.add(this)
    for (supertype in type.getAllSuperclasses()) {
      if (supertype.kind != ClassKind.CLASS) continue
      // TODO do we need to check if it's j.l.Object?
      val irType = supertype.defaultType
      if (irType == pluginContext.irBuiltIns.anyType) {
        // Don't load properties for kotlin.Any/java.lang.Object.
        continue
      }
      result.add(AppliedType(supertype))
    }
    return result
  }

  override fun toString() = type.fqNameWhenAvailable!!.asString()

  companion object {
    operator fun invoke(type: IrClass): AppliedType {
      return AppliedType(type)
    }
  }
}
