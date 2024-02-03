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

import com.squareup.moshi.Json
import dev.zacsweers.moshix.ir.compiler.api.DelegateKey
import dev.zacsweers.moshix.ir.compiler.api.PropertyGenerator
import dev.zacsweers.moshix.ir.compiler.api.TargetProperty
import dev.zacsweers.moshix.ir.compiler.util.error
import dev.zacsweers.moshix.ir.compiler.util.locationIn
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.name.FqName

internal val JSON_ANNOTATION = FqName("com.squareup.moshi.Json")
internal val JSON_QUALIFIER_ANNOTATION = FqName("com.squareup.moshi.JsonQualifier")

internal fun IrAnnotationContainer?.jsonQualifiers(): Set<IrConstructorCall> {
  if (this == null) return emptySet()
  return annotations.filterTo(LinkedHashSet()) {
    it.type.classOrNull?.owner?.hasAnnotation(JSON_QUALIFIER_ANNOTATION) == true
  }
}

internal fun IrProperty.jsonNameFromAnywhere(): String? {
  return jsonName() ?: backingField?.jsonName() ?: getter?.jsonName() ?: setter?.jsonName()
}

internal fun IrProperty.jsonIgnoreFromAnywhere(): Boolean {
  return jsonIgnore() ||
    backingField?.jsonIgnore() == true ||
    getter?.jsonIgnore() == true ||
    setter?.jsonIgnore() == true
}

internal fun IrAnnotationContainer.jsonName(): String? {
  @Suppress("UNCHECKED_CAST")
  return (getAnnotation(JSON_ANNOTATION)?.getValueArgument(0) as? IrConst<String>?)
    ?.value
    ?.takeUnless { it == Json.UNSET_NAME }
}

internal fun IrAnnotationContainer.jsonIgnore(): Boolean {
  @Suppress("UNCHECKED_CAST")
  return (getAnnotation(JSON_ANNOTATION)?.getValueArgument(1) as? IrConst<Boolean>?)?.value == true
}

private val TargetProperty.isSettable
  get() = property.isVar || parameter != null
private val TargetProperty.isVisible: Boolean
  get() {
    return visibility == DescriptorVisibilities.INTERNAL ||
      visibility == DescriptorVisibilities.PROTECTED ||
      visibility == DescriptorVisibilities.PUBLIC
  }

/**
 * Returns a generator for this property, or null if either there is an error and this property
 * cannot be used with code gen, or if no codegen is necessary for this property.
 */
internal fun TargetProperty.generator(
  originalType: IrClass,
  errors: MutableList<(logger: MessageCollector) -> Unit>,
): PropertyGenerator? {
  if (jsonIgnore) {
    if (!hasDefault) {
      errors += {
        it.error(originalType) { "No default value for transient/ignored property $name" }
      }
      return null
    }
    return PropertyGenerator(this, DelegateKey(type, emptyList()), true)
  }

  if (!isVisible) {
    errors += { it.error(originalType) { "property $name is not visible" } }
    return null
  }

  if (!isSettable) {
    return null // This property is not settable. Ignore it.
  }

  // Merge parameter and property annotations
  val qualifiers = parameter?.qualifiers.orEmpty() + property.jsonQualifiers()
  for (jsonQualifier in qualifiers) {
    val qualifierRawType = jsonQualifier.type.classOrNull!!.owner
    val retentionValue =
      qualifierRawType.getAnnotation(FqName("kotlin.annotation.Retention"))?.getValueArgument(0)
        as IrGetEnumValue? ?: continue
    // TODO what about java qualifiers types?
    val retention = retentionValue.symbol.owner.name.identifier
    // Check Java types since that covers both Java and Kotlin annotations.
    if (retention != "RUNTIME") {
      errors += {
        it.error({ jsonQualifier.locationIn(originalType.file) }) {
          "JsonQualifier @${qualifierRawType.name} must have RUNTIME retention"
        }
      }
    }
  }

  return PropertyGenerator(this, DelegateKey(type, qualifiers.toList()))
}
