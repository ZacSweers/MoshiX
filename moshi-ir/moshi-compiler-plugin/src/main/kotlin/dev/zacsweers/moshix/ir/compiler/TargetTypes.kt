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

import dev.zacsweers.moshix.ir.compiler.api.TargetConstructor
import dev.zacsweers.moshix.ir.compiler.api.TargetParameter
import dev.zacsweers.moshix.ir.compiler.api.TargetProperty
import dev.zacsweers.moshix.ir.compiler.api.TargetType
import dev.zacsweers.moshix.ir.compiler.util.check
import dev.zacsweers.moshix.ir.compiler.util.error
import dev.zacsweers.moshix.ir.compiler.util.isTransient
import dev.zacsweers.moshix.ir.compiler.util.locationOf
import dev.zacsweers.moshix.ir.compiler.util.rawType
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.hasDefaultValue
import org.jetbrains.kotlin.ir.util.isEnumClass
import org.jetbrains.kotlin.ir.util.isFromJava
import org.jetbrains.kotlin.ir.util.isLocal
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.util.properties

/** Returns a target type for [type] or null if it cannot be used with code gen. */
internal fun targetType(
    type: IrClass,
    pluginContext: IrPluginContext,
    logger: MessageCollector,
): TargetType? {
  val file = type.file
  val location = { file.locationOf(type) }
  logger.check(!type.isEnumClass, location) {
    "@JsonClass with 'generateAdapter = \"true\"' can't be applied to ${type.fqNameWhenAvailable}: code gen for enums is not supported or necessary"
  }
  logger.check(type.kind == ClassKind.CLASS, location) {
    "@JsonClass can't be applied to ${type.fqNameWhenAvailable}: must be a Kotlin class"
  }
  logger.check(!type.isInner, location) {
    "@JsonClass can't be applied to ${type.fqNameWhenAvailable}: must not be an inner class"
  }
  logger.check(type.modality != Modality.SEALED, location) {
    "@JsonClass can't be applied to ${type.fqNameWhenAvailable}: must not be sealed"
  }
  logger.check(type.modality != Modality.ABSTRACT, location) {
    "@JsonClass can't be applied to ${type.fqNameWhenAvailable}: must not be abstract"
  }
  logger.check(!type.isLocal, location) {
    "@JsonClass can't be applied to ${type.fqNameWhenAvailable}: must not be local"
  }
  val isPublicOrInternal =
      type.visibility == DescriptorVisibilities.PUBLIC ||
          type.visibility == DescriptorVisibilities.INTERNAL
  logger.check(isPublicOrInternal, location) {
    "@JsonClass can't be applied to ${type.fqNameWhenAvailable}: must be internal or public"
  }

  //  val classTypeParamsResolver = type.typeParameters.toTypeParameterResolver(
  //    sourceTypeHint = type.qualifiedName!!.asString()
  //  )
  val typeVariables = type.typeParameters
  val appliedType = AppliedType(type)

  val constructor =
      primaryConstructor(type)
          ?: run {
            logger.error({ type.file.locationOf(type) }) { "No primary constructor found on $type" }
            return null
          }
  if (constructor.visibility != DescriptorVisibilities.INTERNAL &&
      constructor.visibility != DescriptorVisibilities.PUBLIC) {
    logger.error({ type.file.locationOf(constructor.irConstructor) }) {
      "@JsonClass can't be applied to $type: primary constructor is not internal or public"
    }
    return null
  }

  val properties = mutableMapOf<String, TargetProperty>()
  for (superclass in appliedType.superclasses(pluginContext)) {
    val classDecl = superclass.type
    if (classDecl.isFromJava()) {
      logger.error({ type.file.locationOf(type) }) {
        """
          @JsonClass can't be applied to $type: supertype $superclass is not a Kotlin type.
          Origin=${classDecl.origin}
          Annotations=${classDecl.annotations.joinToString(prefix = "[", postfix = "]") { it.type.rawType().name.identifier }}
          """.trimIndent()
      }
      return null
    }
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
      typeName = type.defaultType,
      constructor = constructor,
      properties = properties,
      typeVariables = typeVariables,
      isDataClass = type.isData,
      visibility = resolvedVisibility,
  )
}

// private fun ClassName.withTypeArguments(arguments: List<TypeName>): TypeName {
//  return if (arguments.isEmpty()) {
//    this
//  } else {
//    this.parameterizedBy(arguments)
//  }
// }

// @OptIn(KspExperimental::class)
internal fun primaryConstructor(
    targetType: IrClass,
): TargetConstructor? {
  val primaryConstructor = targetType.primaryConstructor ?: return null

  val parameters = LinkedHashMap<String, TargetParameter>()
  for (parameter in primaryConstructor.valueParameters) {
    val index = parameter.index
    val name = parameter.name.identifier
    parameters[name] =
        TargetParameter(
            name = name,
            index = index,
            type = parameter.type,
            hasDefault = parameter.hasDefaultValue(),
            qualifiers = parameter.jsonQualifiers(),
            jsonIgnore = parameter.jsonIgnore(),
            jsonName = parameter.jsonName() ?: name)
  }

  return TargetConstructor(
      primaryConstructor,
      parameters,
      primaryConstructor.visibility,
  )
}

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
            visibility = property.visibility,
            jsonName = parameter?.jsonName ?: property.jsonName() ?: name,
            jsonIgnore = isTransient || parameter?.jsonIgnore == true || property.jsonIgnore())
  }

  return result
}
