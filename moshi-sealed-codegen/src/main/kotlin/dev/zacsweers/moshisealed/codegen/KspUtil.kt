package dev.zacsweers.moshisealed.codegen

import org.jetbrains.kotlin.ksp.processing.Resolver
import org.jetbrains.kotlin.ksp.symbol.KSAnnotated
import org.jetbrains.kotlin.ksp.symbol.KSAnnotation
import org.jetbrains.kotlin.ksp.symbol.KSClassDeclaration
import org.jetbrains.kotlin.ksp.symbol.KSType

internal inline fun <reified T> Resolver.getClassDeclarationByName(): KSClassDeclaration {
  return getClassDeclarationByName(T::class.qualifiedName!!)
}

internal fun Resolver.getClassDeclarationByName(fqcn: String): KSClassDeclaration {
  return getClassDeclarationByName(getKSNameFromString(fqcn)) ?: error("Class '$fqcn' not found.")
}

internal fun KSClassDeclaration.asType() = asType(emptyList())

internal fun KSAnnotated.getAnnotationWithType(target: KSType): KSAnnotation {
  return findAnnotationWithType(target) ?: error("Annotation $target not found.")
}

internal fun KSAnnotated.hasAnnotation(target: KSType): Boolean {
  return findAnnotationWithType(target) != null
}

internal fun KSAnnotated.findAnnotationWithType(target: KSType): KSAnnotation? {
  return annotations.find { it.annotationType.resolve() == target }
}