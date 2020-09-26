package dev.zacsweers.moshix.sealed.codegen.ksp

import com.google.devtools.ksp.isLocal
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.Nullability.NULLABLE
import com.google.devtools.ksp.symbol.Variance.CONTRAVARIANT
import com.google.devtools.ksp.symbol.Variance.COVARIANT
import com.google.devtools.ksp.symbol.Variance.STAR
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets.UTF_8
import com.squareup.kotlinpoet.STAR as KpStar

fun KSType.toTypeName(): TypeName {
  val type = when (declaration) {
    is KSClassDeclaration -> {
      (declaration as KSClassDeclaration).toTypeName(
        arguments.map { it.type!!.resolve().toTypeName() })
    }
    is KSTypeParameter -> {
      (declaration as KSTypeParameter).toTypeName()
    }
    else -> error("Unsupported type: $declaration")
  }

  val nullable = nullability == NULLABLE

  return type.copy(nullable = nullable)
}

fun KSClassDeclaration.toTypeName(
  actualTypeArgs: List<TypeName> = typeParameters.map { it.toTypeName() },
): TypeName {
  val className = toClassName()
  return if (typeParameters.isNotEmpty()) {
    className.parameterizedBy(actualTypeArgs)
  } else {
    className
  }
}

fun KSClassDeclaration.toClassName(): ClassName {
  require(!isLocal()) {
    "Local/anonymous classes are not supported!"
  }
  val pkgName = packageName.asString()
  val typesString = qualifiedName!!.asString().removePrefix("$pkgName.")

  val simpleNames = typesString
    .split(".")
  return ClassName(pkgName, simpleNames)
}

fun KSTypeParameter.toTypeName(): TypeName {
  if (variance == STAR) return KpStar
  val typeVarName = name.getShortName()
  val typeVarBounds = bounds.map { it.toTypeName() }
  val typeVarVariance = when (variance) {
    COVARIANT -> KModifier.IN
    CONTRAVARIANT -> KModifier.OUT
    else -> null
  }
  return TypeVariableName(typeVarName, bounds = typeVarBounds, variance = typeVarVariance)
}

fun KSTypeReference.toTypeName(): TypeName {
  val type = resolve()
  return type.toTypeName()
}

fun KSAnnotation.toTypeName(): TypeName {
  return annotationType.resolve().toTypeName()
}

fun FileSpec.writeTo(codeGenerator: CodeGenerator) {
  val file = codeGenerator.createNewFile(packageName, name)
  // Don't use writeTo(file) because that tries to handle directories under the hood
  OutputStreamWriter(file, UTF_8)
    .use(::writeTo)
}