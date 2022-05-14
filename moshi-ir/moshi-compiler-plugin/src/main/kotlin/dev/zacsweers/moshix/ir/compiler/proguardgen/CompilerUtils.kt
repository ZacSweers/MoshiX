package dev.zacsweers.moshix.ir.compiler.proguardgen

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.descriptors.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.has
import org.jetbrains.kotlin.platform.jvm.JvmPlatform
import org.jetbrains.kotlin.resolve.constants.ConstantValue
import org.jetbrains.kotlin.resolve.constants.KClassValue
import org.jetbrains.kotlin.resolve.constants.KClassValue.Value.NormalClass
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.getAllSuperClassifiers
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperClassNotAny
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperInterfaces
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.types.ErrorType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.typeUtil.supertypes

internal fun ClassDescriptor.annotationOrNull(
  annotationFqName: FqName,
  scope: FqName? = null
): AnnotationDescriptor? {
  // Must be JVM, we don't support anything else.
  if (!module.platform.has<JvmPlatform>()) return null
  val annotationDescriptor =
    try {
      annotations.findAnnotation(annotationFqName)
    } catch (e: IllegalStateException) {
      // In some scenarios this exception is thrown. Throw a new exception with a better
      // explanation.
      // Caused by: java.lang.IllegalStateException: Should not be called!
      // at org.jetbrains.kotlin.types.ErrorUtils$1.getPackage(ErrorUtils.java:95)
      throw MoshiCompilationException(
        this,
        message =
          "It seems like you tried to contribute an inner class to its outer class. This " +
            "is not supported and results in a compiler error.",
        cause = e
      )
    }
  return if (scope == null || annotationDescriptor == null) {
    annotationDescriptor
  } else {
    annotationDescriptor.takeIf { scope == it.scope(module).fqNameSafe }
  }
}

internal fun ClassDescriptor.annotation(
  annotationFqName: FqName,
  scope: FqName? = null
): AnnotationDescriptor =
  requireNotNull(annotationOrNull(annotationFqName, scope)) {
    "Couldn't find $annotationFqName with scope $scope for $fqNameSafe."
  }

/**
 * Returns only the super class (excluding [Any]) and implemented interfaces declared directly by
 * this class. This is different from [getAllSuperClassifiers] in that the latter returns the entire
 * hierarchy.
 */
internal fun ClassDescriptor.directSuperClassAndInterfaces(): List<ClassDescriptor> {
  return listOfNotNull(getSuperClassNotAny()).plus(getSuperInterfaces())
}

// When the Kotlin type is of the form: KClass<OurType>.
internal fun KotlinType.argumentType(): KotlinType = arguments.first().type

internal fun KotlinType.classDescriptorOrNull(): ClassDescriptor? {
  return TypeUtils.getClassDescriptor(this)
}

internal fun KotlinType.requireClassDescriptor(): ClassDescriptor {
  return classDescriptorOrNull()
    ?: throw MoshiCompilationException("Unable to resolve type for ${this.asTypeName()}")
}

internal fun AnnotationDescriptor.getAnnotationValue(key: String): ConstantValue<*>? =
  allValueArguments[Name.identifier(key)]

internal fun AnnotationDescriptor.scope(module: ModuleDescriptor): ClassDescriptor {
  val annotationValue =
    getAnnotationValue("scope") as? KClassValue
      ?: throw MoshiCompilationException(
        annotationDescriptor = this,
        message = "Couldn't find scope for $fqName."
      )

  return annotationValue.argumentType(module).requireClassDescriptor()
}

internal fun ConstantValue<*>.argumentType(module: ModuleDescriptor): KotlinType {
  val argumentType = getType(module).argumentType()
  if (argumentType !is ErrorType) return argumentType

  // Handle inner classes explicitly. When resolving the Kotlin type of inner class from
  // dependencies the compiler might fail. It tries to load my.package.Class$Inner and fails
  // whereas is should load my.package.Class.Inner.
  val normalClass = this.value
  if (normalClass !is NormalClass) return argumentType

  val classId = normalClass.value.classId

  return module
    .findClassAcrossModuleDependencies(
      classId =
        ClassId(
          classId.packageFqName,
          FqName(classId.relativeClassName.asString().replace('$', '.')),
          false
        )
    )
    ?.defaultType
    ?: throw MoshiCompilationException(
      "Couldn't resolve class across module dependencies for class ID: $classId"
    )
}

internal fun List<KotlinType>.getAllSuperTypes(): Sequence<FqName> =
  generateSequence(this) { kotlinTypes ->
      kotlinTypes.ifEmpty { null }?.flatMap { it.supertypes() }
    }
    .flatMap { it.asSequence() }
    .map { it.requireClassDescriptor().fqNameSafe }

/**
 * This function should only be used for package names. If the FqName is the root (no package at
 * all), then this function returns an empty string whereas `toString()` would return "<root>". For
 * a more convenient string concatenation the returned result can be prefixed and suffixed with an
 * additional dot. The root package never will use a prefix or suffix.
 */
internal fun FqName.safePackageString(
  dotPrefix: Boolean = false,
  dotSuffix: Boolean = true
): String =
  if (isRoot) {
    ""
  } else {
    val prefix = if (dotPrefix) "." else ""
    val suffix = if (dotSuffix) "." else ""
    "$prefix$this$suffix"
  }

internal fun FqName.classIdBestGuess(): ClassId {
  val segments = pathSegments().map { it.asString() }
  val classNameIndex = segments.indexOfFirst { it[0].isUpperCase() }
  if (classNameIndex < 0) {
    return ClassId.topLevel(this)
  }

  val packageFqName = FqName.fromSegments(segments.subList(0, classNameIndex))
  val relativeClassName = FqName.fromSegments(segments.subList(classNameIndex, segments.size))
  return ClassId(packageFqName, relativeClassName, false)
}
