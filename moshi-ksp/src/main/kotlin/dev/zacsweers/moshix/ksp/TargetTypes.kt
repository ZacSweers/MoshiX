package dev.zacsweers.moshix.ksp

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.isInternal
import com.google.devtools.ksp.isLocal
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import com.squareup.moshi.Json
import com.squareup.moshi.JsonQualifier
import dev.zacsweers.moshix.ksp.shade.api.TargetConstructor
import dev.zacsweers.moshix.ksp.shade.api.TargetParameter
import dev.zacsweers.moshix.ksp.shade.api.TargetProperty
import dev.zacsweers.moshix.ksp.shade.api.TargetType

private val OBJECT_CLASS = java.lang.Object::class.asClassName()

/** Returns a target type for `element`, or null if it cannot be used with code gen. */
internal fun targetType(
  type: KSClassDeclaration,
  resolver: Resolver,
  logger: KSPLogger,
): TargetType? {
  require(type.classKind != ClassKind.ENUM_CLASS) {
    "@JsonClass with 'generateAdapter = \"true\"' can't be applied to $type: code gen for enums is not supported or necessary"
  }
  require(type.classKind == ClassKind.CLASS) {
    "@JsonClass can't be applied to $type: must be a Kotlin class"
  }
  require(Modifier.INNER !in type.modifiers) {
    "@JsonClass can't be applied to $type: must not be an inner class"
  }
  require(Modifier.SEALED !in type.modifiers) {
    "@JsonClass can't be applied to $type: must not be sealed"
  }
  require(Modifier.ABSTRACT !in type.modifiers) {
    "@JsonClass can't be applied to $type: must not be abstract"
  }
  require(!type.isLocal()) {
    "@JsonClass can't be applied to $type: must not be local"
  }
  require(type.isPublic() || type.isInternal()) {
    "@JsonClass can't be applied to $type: must be internal or public"
  }

  val typeVariables = type.typeParameters.map { it.toTypeName() }
  logger.logging("[KSP] Type is $type")
  logger.logging("[KSP] Type variables are $typeVariables")
  val appliedType = AppliedType.get(type)

  val constructor = primaryConstructor(resolver, type)
    ?: error("No primary constructor found on $type")
  if (constructor.visibility != KModifier.INTERNAL && constructor.visibility != KModifier.PUBLIC) {
    error("@JsonClass can't be applied to $type: " +
      "primary constructor is not internal or public")
  }

  val properties = mutableMapOf<String, TargetProperty>()

  val resolvedTypes = mutableListOf<ResolvedTypeMapping>()
  val superTypes = appliedType.supertypes(resolver)
    .filterNot { supertype ->
      supertype.typeName == OBJECT_CLASS || // Don't load properties for java.lang.Object.
        supertype.type.classKind != ClassKind.CLASS  // Don't load properties for interface types.
    }
    .onEach { supertype ->
      if (supertype.type.hasAnnotation(resolver.getClassDeclarationByName<Metadata>().asType())) {
        println(
          "@JsonClass can't be applied to $type: supertype $supertype is not a Kotlin type: $type")
        return null
      }
    }
    .associateWithTo(LinkedHashMap()) { supertype ->
      // Load the kotlin API cache into memory eagerly so we can reuse the parsed APIs
      val api = supertype.type

//        val apiSuperClass = api.getAllSuperTypes().first()
//        if (apiSuperClass.arguments.isNotEmpty()) {
//          //
//          // This extends a typed generic superclass. We want to construct a mapping of the
//          // superclass typevar names to their materialized types here.
//          //
//          // class Foo extends Bar<String>
//          // class Bar<T>
//          //
//          // We will store {Foo : {T : [String]}}.
//          //
//          // Then when we look at Bar<T> later, we'll look up to the descendent Foo and extract its
//          // materialized type from there.
//          //
//          val untyped = supertype.type
//
//          // Convert to an element and back to wipe the typed generics off of this
//          resolvedTypes += ResolvedTypeMapping(
//              target = untyped.toClassName(),
//              args = supertype.typeName.asSequence()
//                  .cast<TypeVariableName>()
//                  .map(TypeVariableName::name)
//                  .zip(apiSuperClass.arguments.asSequence())
//                  .associate { it }
//          )
//        }

      return@associateWithTo api
    }

  for ((localAppliedType, supertypeApi) in superTypes.entries) {
    val appliedClassName = localAppliedType.type.toClassName()
    val supertypeProperties = declaredProperties(
      constructor = constructor,
      kotlinApi = supertypeApi,
      allowedTypeVars = typeVariables.filterIsInstance<TypeVariableName>().toSet(), // TODO what about stars?
      currentClass = appliedClassName,
      resolvedTypes = resolvedTypes,
      resolver = resolver
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
    typeName = type.toTypeName(),
    constructor = constructor,
    properties = properties,
    typeVariables = typeVariables.filterIsInstance<TypeVariableName>().also {
      logger.logging("[KSP] Filtered typevars: $it")
    },
    isDataClass = Modifier.DATA in type.modifiers,
    visibility = resolvedVisibility)
}

internal fun primaryConstructor(
  resolver: Resolver,
  targetType: KSClassDeclaration,
): TargetConstructor? {
  val primaryConstructor = targetType.primaryConstructor ?: return null

  val parameters = LinkedHashMap<String, TargetParameter>()
  for ((index, parameter) in primaryConstructor.parameters.withIndex()) {
    val name = parameter.name!!.getShortName()
    parameters[name] = TargetParameter(
      name = name,
      index = index,
      type = parameter.type!!.toTypeName(),
      hasDefault = parameter.hasDefault,
      qualifiers = parameter.qualifiers(resolver),
      jsonName = parameter.jsonName(resolver)
    )
  }

  val kmConstructorSignature: String? = resolver.mapToJvmSignature(primaryConstructor)
  return TargetConstructor(parameters, primaryConstructor.getVisibility().asKModifier(),
    kmConstructorSignature)
}

/**
 * Represents a resolved raw class to type arguments where [args] are a map of the parent type var
 * name to its resolved [TypeName].
 */
private data class ResolvedTypeMapping(val target: ClassName, val args: Map<String, TypeName>)

private fun resolveTypeArgs(
  targetClass: ClassName,
  propertyType: TypeName,
  resolvedTypes: List<ResolvedTypeMapping>,
  allowedTypeVars: Set<TypeVariableName>,
  entryStartIndex: Int = resolvedTypes.indexOfLast { it.target == targetClass },
): TypeName {
  // TODO do we need the rest?
  return propertyType
//  val unwrappedType = propertyType.unwrapTypeAlias()
//
//  if (unwrappedType !is TypeVariableName) {
//    return unwrappedType
//  } else if (entryStartIndex == -1) {
//    return unwrappedType
//  }
//
//  val targetMappingIndex = resolvedTypes[entryStartIndex]
//  val targetMappings = targetMappingIndex.args
//
//  // Try to resolve the real type of this property based on mapped generics in the subclass.
//  // We need to us a non-nullable version for mapping since we're just mapping based on raw java
//  // type vars, but then can re-copy nullability back if it is found.
//  val resolvedType = targetMappings[unwrappedType.name]
//    ?.copy(nullable = unwrappedType.isNullable)
//    ?: unwrappedType
//
//  return when {
//    resolvedType !is TypeVariableName -> resolvedType
//    entryStartIndex != 0 -> {
//      // We need to go deeper
//      resolveTypeArgs(targetClass, resolvedType, resolvedTypes, allowedTypeVars, entryStartIndex - 1)
//    }
//    resolvedType.copy(nullable = false) in allowedTypeVars -> {
//      // This is a generic type in the top-level declared class. This is fine to leave in because
//      // this will be handled by the `Type` array passed in at runtime.
//      resolvedType
//    }
//    else -> error("Could not find $resolvedType in $resolvedTypes. Also not present in allowable top-level type vars $allowedTypeVars")
//  }
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
  kotlinApi: KSClassDeclaration,
  allowedTypeVars: Set<TypeVariableName>,
  currentClass: ClassName,
  resolvedTypes: List<ResolvedTypeMapping>,
  resolver: Resolver,
): Map<String, TargetProperty> {

  val result = mutableMapOf<String, TargetProperty>()
  for (property in kotlinApi.getDeclaredProperties()) {
//    val resolvedType = resolveTypeArgs(
//      targetClass = currentClass,
//      propertyType = initialProperty.type,
//      resolvedTypes = resolvedTypes,
//      allowedTypeVars = allowedTypeVars
//    )
//    val property = initialProperty.toBuilder(type = resolvedType).build()
    val propertySpec = property.toPropertySpec(resolver)
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

private fun KSPropertyDeclaration.toPropertySpec(resolver: Resolver): PropertySpec {
  return PropertySpec.builder(simpleName.getShortName(), type.toTypeName())
    .mutable(isMutable)
    .addModifiers(modifiers.map { KModifier.valueOf(it.name) })
    .apply {
      if (hasAnnotation(resolver.getClassDeclarationByName<Transient>().asType())) {
        addAnnotation(Transient::class.java)
      }
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

private fun <E> Sequence<*>.cast(): Sequence<E> {
  return map {
    @Suppress("UNCHECKED_CAST")
    it as E
  }
}