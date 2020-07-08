package dev.zacsweers.moshisealed.codegen

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier
import org.jetbrains.kotlin.ksp.processing.Resolver
import org.jetbrains.kotlin.ksp.symbol.KSAnnotated
import org.jetbrains.kotlin.ksp.symbol.KSAnnotation
import org.jetbrains.kotlin.ksp.symbol.KSClassDeclaration
import org.jetbrains.kotlin.ksp.symbol.KSName
import org.jetbrains.kotlin.ksp.symbol.KSType
import org.jetbrains.kotlin.ksp.symbol.Visibility
import org.jetbrains.kotlin.ksp.symbol.Visibility.INTERNAL
import org.jetbrains.kotlin.ksp.symbol.Visibility.JAVA_PACKAGE
import org.jetbrains.kotlin.ksp.symbol.Visibility.LOCAL
import org.jetbrains.kotlin.ksp.symbol.Visibility.PRIVATE
import org.jetbrains.kotlin.ksp.symbol.Visibility.PROTECTED
import org.jetbrains.kotlin.ksp.symbol.Visibility.PUBLIC

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

internal inline fun <reified T> KSAnnotation.getMember(name: String): T {
  return arguments.find { it.name?.getShortName() == name }
      ?.value as? T
      ?: error("No member name found for $name.")
}

internal fun Visibility.asKModifier(): KModifier {
  return when (this) {
    PUBLIC -> KModifier.PUBLIC
    PRIVATE -> KModifier.PRIVATE
    PROTECTED -> KModifier.PROTECTED
    INTERNAL -> KModifier.INTERNAL
    JAVA_PACKAGE -> KModifier.PUBLIC
    LOCAL -> error("Local is unsupported")
  }
}

internal fun KSAnnotation.toAnnotationSpec(resolver: Resolver): AnnotationSpec {
  val element = annotationType.resolve()!!.declaration as KSClassDeclaration
  // TODO support generic annotations
  val builder = AnnotationSpec.builder(element.toClassName())
  for (argument in arguments) {
    val member = CodeBlock.builder()
    val name = argument.name!!.getShortName()
    member.add("%L = ", name)
    when (val value = argument.value!!) {
      resolver.builtIns.arrayType -> {
//        TODO("Arrays aren't supported tet")
//        member.add("[⇥⇥")
//        values.forEachIndexed { index, value ->
//          if (index > 0) member.add(", ")
//          value.accept(this, name)
//        }
//        member.add("⇤⇤]")
      }
      // TODO https://github.com/android/kotlin/issues/13
      is KSType -> member.add("%T::class", value.toTypeName())
      // TODO is this the right way to handle an enum constant?
      is KSName -> member.add("%T.%L", ClassName.bestGuess(value.getQualifier()), value.getShortName())
      is KSAnnotation -> member.add("%L", value.toAnnotationSpec(resolver))
      else -> member.add(memberForValue(value))
    }
    builder.addMember(member.build())
  }
  return builder.build()
}

/**
 * Creates a [CodeBlock] with parameter `format` depending on the given `value` object.
 * Handles a number of special cases, such as appending "f" to `Float` values, and uses
 * `%L` for other types.
 */
internal fun memberForValue(value: Any) = when (value) {
  is Class<*> -> CodeBlock.of("%T::class", value)
  is Enum<*> -> CodeBlock.of("%T.%L", value.javaClass, value.name)
  is String -> CodeBlock.of("%S", value)
  is Float -> CodeBlock.of("%Lf", value)
//  is Char -> CodeBlock.of("'%L'", characterLiteralWithoutSingleQuotes(value)) // TODO public?
  else -> CodeBlock.of("%L", value)
}