package dev.zacsweers.moshix.ksp

import com.google.devtools.ksp.isLocal
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.Variance.CONTRAVARIANT
import com.google.devtools.ksp.symbol.Variance.COVARIANT
import com.google.devtools.ksp.symbol.Variance.INVARIANT
import com.google.devtools.ksp.symbol.Variance.STAR
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets.UTF_8
import com.squareup.kotlinpoet.STAR as KpStar

internal fun KSType.toClassName(): ClassName {
  val decl = declaration
  check(decl is KSClassDeclaration)
  return decl.toClassName()
}

internal fun KSType.toTypeName(typeParamResolver: TypeParameterResolver): TypeName {
  val type = when (val decl = declaration) {
    is KSClassDeclaration -> decl.toTypeName(arguments.map { it.toTypeName(typeParamResolver) })
    is KSTypeParameter -> typeParamResolver[decl.name.getShortName()]
    is KSTypeAlias -> decl.type.resolve().toTypeName(typeParamResolver)
    else -> error("Unsupported type: $declaration")
  }

  return type.copy(nullable = isMarkedNullable)
}

internal fun KSClassDeclaration.toTypeName(argumentList: List<TypeName> = emptyList()): TypeName {
  val className = toClassName()
  return if (argumentList.isNotEmpty()) {
    className.parameterizedBy(argumentList)
  } else {
    className
  }
}

internal interface TypeParameterResolver {
  val parametersMap: Map<String, TypeVariableName>
  operator fun get(index: String): TypeVariableName
}

internal fun List<KSTypeParameter>.toTypeParameterResolver(
  fallback: TypeParameterResolver? = null,
  sourceType: String? = null,
): TypeParameterResolver {
  val parametersMap = LinkedHashMap<String, TypeVariableName>()
  val typeParamResolver = { id: String ->
    parametersMap[id]
      ?: fallback?.get(id)
      ?: throw IllegalStateException("No type argument found for $id! Anaylzing $sourceType")
  }

  val resolver = object : TypeParameterResolver {
    override val parametersMap: Map<String, TypeVariableName> = parametersMap

    override operator fun get(index: String): TypeVariableName = typeParamResolver(index)
  }

  // Fill the parametersMap. Need to do sequentially and allow for referencing previously defined params
  for (typeVar in this) {
    // Put the simple typevar in first, then it can be referenced in the full toTypeVariable()
    // replacement later that may add bounds referencing this.
    val id = typeVar.name.getShortName()
    parametersMap[id] = TypeVariableName(id)
    // Now replace it with the full version.
    parametersMap[id] = typeVar.toTypeVariableName(resolver)
  }

  return resolver
}

internal fun KSClassDeclaration.toClassName(): ClassName {
  require(!isLocal()) {
    "Local/anonymous classes are not supported!"
  }
  val pkgName = packageName.asString()
  val typesString = qualifiedName!!.asString().removePrefix("$pkgName.")

  val simpleNames = typesString
    .split(".")
  return ClassName(pkgName, simpleNames)
}

internal fun KSTypeParameter.toTypeName(typeParamResolver: TypeParameterResolver): TypeName {
  if (variance == STAR) return KpStar
  return toTypeVariableName(typeParamResolver)
}

internal fun KSTypeParameter.toTypeVariableName(
  typeParamResolver: TypeParameterResolver,
): TypeVariableName {
  val typeVarName = name.getShortName()
  val typeVarBounds = bounds.map { it.toTypeName(typeParamResolver) }
  val typeVarVariance = when (variance) {
    COVARIANT -> KModifier.OUT
    CONTRAVARIANT -> KModifier.IN
    else -> null
  }
  return TypeVariableName(typeVarName, bounds = typeVarBounds, variance = typeVarVariance)
}

internal fun KSTypeArgument.toTypeName(typeParamResolver: TypeParameterResolver): TypeName {
  val typeName = type?.resolve()?.toTypeName(typeParamResolver) ?: return KpStar
  return when (variance) {
    COVARIANT -> WildcardTypeName.producerOf(typeName)
    CONTRAVARIANT -> WildcardTypeName.consumerOf(typeName)
    STAR -> KpStar
    INVARIANT -> typeName
  }
}

internal fun KSTypeReference.toTypeName(typeParamResolver: TypeParameterResolver): TypeName {
  val type = resolve()
  return type.toTypeName(typeParamResolver)
}

internal fun FileSpec.writeTo(codeGenerator: CodeGenerator) {
  val dependencies = Dependencies(false, *originatingKSFiles().toTypedArray())
  val file = codeGenerator.createNewFile(dependencies, packageName, name)
  // Don't use writeTo(file) because that tries to handle directories under the hood
  OutputStreamWriter(file, UTF_8)
    .use(::writeTo)
}