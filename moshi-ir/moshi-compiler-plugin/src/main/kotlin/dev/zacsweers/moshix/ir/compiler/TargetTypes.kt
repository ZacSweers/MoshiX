// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.ir.compiler

import dev.zacsweers.moshix.ir.compiler.api.TargetConstructor
import dev.zacsweers.moshix.ir.compiler.api.TargetParameter
import dev.zacsweers.moshix.ir.compiler.api.TargetProperty
import dev.zacsweers.moshix.ir.compiler.api.TargetType
import dev.zacsweers.moshix.ir.compiler.util.isTransient
import org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.hasDefaultValue
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.util.properties

internal fun targetType(
  type: IrClass,
  pluginContext: IrPluginContext,
): TargetType {
  val typeVariables = type.typeParameters
  val appliedType = AppliedType(type)
  val constructor = primaryConstructor(type)

  val properties = mutableMapOf<String, TargetProperty>()
  for (superclass in appliedType.superclasses(pluginContext)) {
    val classDecl = superclass.type
    val supertypeProperties = declaredProperties(constructor = constructor, classDecl = classDecl)
    for ((name, property) in supertypeProperties) {
      properties.putIfAbsent(name, property)
    }
  }
  val visibility = type.visibility
  // If any class in the enclosing class hierarchy is internal, they must all have internal
  // generated adapters.
  val resolvedVisibility =
    if (visibility == DescriptorVisibilities.INTERNAL) {
      // Our nested type is already internal, no need to search
      visibility
    } else {
      // Implicitly public, so now look up the hierarchy
      val forceInternal =
        generateSequence<IrDeclaration>(type) { it.parentClassOrNull }
          .filterIsInstance<IrClass>()
          .any { it.visibility == DescriptorVisibilities.INTERNAL }
      if (forceInternal) DescriptorVisibilities.INTERNAL else visibility
    }
  return TargetType(
    irClass = type,
    irType = type.defaultType,
    constructor = constructor,
    properties = properties,
    typeVariables = typeVariables,
    isDataClass = type.isData,
    visibility = resolvedVisibility,
  )
}

@OptIn(UnsafeDuringIrConstructionAPI::class, DeprecatedForRemovalCompilerApi::class)
internal fun primaryConstructor(targetType: IrClass): TargetConstructor {
  val primaryConstructor = targetType.primaryConstructor!!

  val parameters = LinkedHashMap<String, TargetParameter>()
  for (parameter in primaryConstructor.nonDispatchParameters) {
    val index = parameter.indexInParameters
    val name = parameter.name.identifier
    parameters[name] =
      TargetParameter(
        name = name,
        index = index,
        type = parameter.type,
        hasDefault = parameter.hasDefaultValue(),
        qualifiers = parameter.jsonQualifiers(),
        jsonIgnore = parameter.jsonIgnore(),
        jsonName = parameter.jsonName(),
      )
  }

  return TargetConstructor(primaryConstructor, parameters)
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun declaredProperties(
  constructor: TargetConstructor,
  classDecl: IrClass,
): Map<String, TargetProperty> {
  val result = mutableMapOf<String, TargetProperty>()
  for (property in classDecl.properties) {
    val name = property.name.identifier
    val parameter = constructor.parameters[name]
    // TODO what about java/modifier transient?
    val isTransient = property.isTransient
    result[name] =
      TargetProperty(
        property = property,
        parameter = parameter,
        jsonName = property.jsonNameFromAnywhere() ?: parameter?.jsonName ?: name,
        jsonIgnore =
          isTransient || parameter?.jsonIgnore == true || property.jsonIgnoreFromAnywhere(),
      )
  }

  return result
}
