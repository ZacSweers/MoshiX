package dev.zacsweers.moshix.ksp

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.isInternal
import com.google.devtools.ksp.isLocal
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.ClassKind.CLASS
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.moshi.Json
import com.squareup.moshi.JsonQualifier
import dev.zacsweers.moshix.ksp.shade.api.TargetConstructor
import dev.zacsweers.moshix.ksp.shade.api.TargetParameter
import dev.zacsweers.moshix.ksp.shade.api.TargetProperty
import dev.zacsweers.moshix.ksp.shade.api.TargetType

/** Returns a target type for `element`, or null if it cannot be used with code gen. */
internal fun targetType(
  type: KSClassDeclaration,
  resolver: Resolver,
  logger: KSPLogger,
): TargetType {
  logger.check(type.classKind != ClassKind.ENUM_CLASS, type) {
    "@JsonClass with 'generateAdapter = \"true\"' can't be applied to ${type.qualifiedName?.asString()}: code gen for enums is not supported or necessary"
  }
  logger.check(type.classKind == CLASS, type) {
    "@JsonClass can't be applied to ${type.qualifiedName?.asString()}: must be a Kotlin class"
  }
  logger.check(Modifier.INNER !in type.modifiers, type) {
    "@JsonClass can't be applied to ${type.qualifiedName?.asString()}: must not be an inner class"
  }
  logger.check(Modifier.SEALED !in type.modifiers, type) {
    "@JsonClass can't be applied to ${type.qualifiedName?.asString()}: must not be sealed"
  }
  logger.check(Modifier.ABSTRACT !in type.modifiers, type) {
    "@JsonClass can't be applied to ${type.qualifiedName?.asString()}: must not be abstract"
  }
  logger.check(!type.isLocal(), type) {
    "@JsonClass can't be applied to ${type.qualifiedName?.asString()}: must not be local"
  }
  logger.check(type.isPublic() || type.isInternal(), type) {
    "@JsonClass can't be applied to ${type.qualifiedName?.asString()}: must be internal or public"
  }

  val classTypeParamsResolver = type.typeParameters.toTypeParameterResolver(
    sourceType = type.qualifiedName!!.asString())
  val typeVariables = type.typeParameters.map { it.toTypeVariableName(classTypeParamsResolver) }
  val appliedType = AppliedType.get(type)

  val constructor = primaryConstructor(resolver, type, classTypeParamsResolver)
    ?: logger.errorAndThrow("No primary constructor found on $type", type)
  if (constructor.visibility != KModifier.INTERNAL && constructor.visibility != KModifier.PUBLIC) {
    logger.errorAndThrow("@JsonClass can't be applied to $type: " +
      "primary constructor is not internal or public", type)
  }

  val properties = mutableMapOf<String, TargetProperty>()

  val originalType = appliedType.type
  for (supertype in appliedType.supertypes(resolver)) {
    val classDecl = supertype.type
    if (!classDecl.isKotlinClass(resolver)) {
      logger.errorAndThrow(
        "@JsonClass can't be applied to $type: supertype $supertype is not a Kotlin type: $type")
    }
    val supertypeProperties = declaredProperties(
      constructor = constructor,
      originalType = originalType,
      classDecl = classDecl,
      resolver = resolver,
      // TODO cache this?
      typeParameterResolver = classDecl.typeParameters
        .toTypeParameterResolver(classTypeParamsResolver)
    )
    for ((name, property) in supertypeProperties) {
      properties.putIfAbsent(name, property)
    }
  }
  val visibility = type.getVisibility().asKModifier()
  // If any class in the enclosing class hierarchy is internal, they must all have internal
  // generated adapters.
  val resolvedVisibility = if (visibility == KModifier.INTERNAL) {
    // Our nested type is already internal, no need to search
    visibility
  } else {
    // Implicitly public, so now look up the hierarchy
    val forceInternal = generateSequence<KSDeclaration>(type) { it.parentDeclaration }
      .filterIsInstance<KSClassDeclaration>()
      .any { it.isInternal() }
    if (forceInternal) KModifier.INTERNAL else visibility
  }
  return TargetType(
    typeName = type.toTypeName(typeVariables),
    constructor = constructor,
    properties = properties,
    typeVariables = typeVariables,
    isDataClass = Modifier.DATA in type.modifiers,
    visibility = resolvedVisibility)
}

internal fun primaryConstructor(
  resolver: Resolver,
  targetType: KSClassDeclaration,
  typeParameterResolver: TypeParameterResolver,
): TargetConstructor? {
  val primaryConstructor = targetType.primaryConstructor ?: return null

  val parameters = LinkedHashMap<String, TargetParameter>()
  for ((index, parameter) in primaryConstructor.parameters.withIndex()) {
    val name = parameter.name!!.getShortName()
    parameters[name] = TargetParameter(
      name = name,
      index = index,
      type = parameter.type!!.toTypeName(typeParameterResolver),
      hasDefault = parameter.hasDefault,
      qualifiers = parameter.qualifiers(resolver),
      jsonName = parameter.jsonName(resolver)
    )
  }

  val kmConstructorSignature: String = resolver.mapToJvmSignature(primaryConstructor)
  return TargetConstructor(parameters, primaryConstructor.getVisibility().asKModifier(),
    kmConstructorSignature)
}

private fun KSAnnotated?.qualifiers(resolver: Resolver): Set<AnnotationSpec> {
  if (this == null) return setOf()
  val jsonQualifierType = resolver.getClassDeclarationByName<JsonQualifier>().asType()
  return annotations
    .filter {
      it.annotationType.resolve().declaration.findAnnotationWithType(jsonQualifierType) != null
    }
    .mapTo(mutableSetOf()) {
      it.toAnnotationSpec(resolver)
    }
}

private fun KSAnnotated?.jsonName(resolver: Resolver): String? {
  if (this == null) return null
  val jsonType = resolver.getClassDeclarationByName<Json>().asType()
  return findAnnotationWithType(jsonType)?.getMember<String>("name")
}

private fun declaredProperties(
  constructor: TargetConstructor,
  originalType: KSClassDeclaration,
  classDecl: KSClassDeclaration,
  resolver: Resolver,
  typeParameterResolver: TypeParameterResolver,
): Map<String, TargetProperty> {

  val result = mutableMapOf<String, TargetProperty>()
  for (property in classDecl.getDeclaredProperties()) {
    val initialType = property.type.resolve()
    val resolvedType = if (initialType.declaration is KSTypeParameter) {
      resolver.asMemberOf(property, originalType.asType())
    } else {
      initialType
    }
    val propertySpec = property.toPropertySpec(resolver, resolvedType, typeParameterResolver)
    val name = propertySpec.name
    val parameter = constructor.parameters[name]
    result[name] = TargetProperty(
      propertySpec = propertySpec,
      parameter = parameter,
      visibility = property.modifiers.map { KModifier.valueOf(it.name) }.visibility(),
      jsonName = parameter?.jsonName ?: property.jsonName(resolver)
      ?: name.escapeDollarSigns()
    )
  }

  return result
}

private fun KSPropertyDeclaration.toPropertySpec(
  resolver: Resolver,
  resolvedType: KSType,
  typeParameterResolver: TypeParameterResolver,
): PropertySpec {
  return PropertySpec.builder(
    name = simpleName.getShortName(),
    type = resolvedType.toTypeName(typeParameterResolver)
  )
    .mutable(isMutable)
    .addModifiers(modifiers.map { KModifier.valueOf(it.name) })
    .apply {
      if (hasAnnotation(resolver.getClassDeclarationByName<Transient>().asType())) {
        addAnnotation(Transient::class.java)
      }
      addAnnotations(this@toPropertySpec.annotations.mapNotNull {
        if ((it.annotationType.resolve().declaration as KSClassDeclaration).isJsonQualifier(
            resolver)) {
          it.toAnnotationSpec(resolver)
        } else {
          null
        }
      })
    }
    .build()
}

private fun String.escapeDollarSigns(): String {
  return replace("\$", "\${\'\$\'}")
}

internal fun TypeName.unwrapTypeAlias(): TypeName {
  // TODO do we need to unwrap?
  return this
//  return mapTypes<ClassName> {
//    tag<TypeNameAliasTag>()?.type?.let { unwrappedType ->
//      // If any type is nullable, then the whole thing is nullable
//      var isAnyNullable = isNullable
//      // Keep track of all annotations across type levels. Sort them too for consistency.
//      val runningAnnotations = TreeSet<AnnotationSpec>(compareBy { it.toString() }).apply {
//        addAll(annotations)
//      }
//      val nestedUnwrappedType = unwrappedType.unwrapTypeAlias()
//      runningAnnotations.addAll(nestedUnwrappedType.annotations)
//      isAnyNullable = isAnyNullable || nestedUnwrappedType.isNullable
//      nestedUnwrappedType.copy(nullable = isAnyNullable, annotations = runningAnnotations.toList())
//    }
//  }
}
