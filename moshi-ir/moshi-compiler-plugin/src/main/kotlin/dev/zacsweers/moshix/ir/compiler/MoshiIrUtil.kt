// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.ir.compiler

import com.squareup.moshi.Json
import dev.zacsweers.moshix.ir.compiler.api.DelegateKey
import dev.zacsweers.moshix.ir.compiler.api.PropertyGenerator
import dev.zacsweers.moshix.ir.compiler.api.TargetProperty
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.nonDispatchArguments
import org.jetbrains.kotlin.name.FqName

internal val JSON_ANNOTATION = FqName("com.squareup.moshi.Json")
internal val JSON_QUALIFIER_ANNOTATION = FqName("com.squareup.moshi.JsonQualifier")

@OptIn(UnsafeDuringIrConstructionAPI::class)
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
  return getAnnotation(JSON_ANNOTATION)?.constArgumentOfTypeAt<String>(0)?.takeUnless {
    it == Json.UNSET_NAME
  }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal fun <T> IrConstructorCall.constArgumentOfTypeAt(position: Int): T? {
  @Suppress("UNCHECKED_CAST")
  return (nonDispatchArguments[position] as? IrConst?)?.value as? T?
}

internal fun <T> IrConst.valueAs(): T {
  @Suppress("UNCHECKED_CAST")
  return value as T
}

internal fun IrAnnotationContainer.jsonIgnore(): Boolean {
  @Suppress("UNCHECKED_CAST")
  return getAnnotation(JSON_ANNOTATION)?.constArgumentOfTypeAt<Boolean>(1) == true
}

private val TargetProperty.isSettable
  get() = property.isVar || parameter != null

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal fun TargetProperty.generator(): PropertyGenerator? {
  if (jsonIgnore) {
    return PropertyGenerator(this, DelegateKey(type, emptyList()), true)
  }

  if (!isSettable) {
    return null
  }

  // Merge parameter and property annotations
  val qualifiers = parameter?.qualifiers.orEmpty() + property.jsonQualifiers()
  return PropertyGenerator(this, DelegateKey(type, qualifiers.toList()))
}
