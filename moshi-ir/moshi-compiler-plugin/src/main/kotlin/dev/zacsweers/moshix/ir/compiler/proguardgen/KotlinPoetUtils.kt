package dev.zacsweers.moshix.ir.compiler.proguardgen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.descriptorUtil.parents
import org.jetbrains.kotlin.resolve.descriptorUtil.parentsWithSelf
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.Variance.INVARIANT
import org.jetbrains.kotlin.types.Variance.IN_VARIANCE
import org.jetbrains.kotlin.types.Variance.OUT_VARIANCE
import org.jetbrains.kotlin.types.typeUtil.isTypeParameter

internal fun KtClassOrObject.asClassName(): ClassName =
  ClassName(
    packageName =
      containingKtFile.packageFqName.safePackageString(dotPrefix = false, dotSuffix = false),
    simpleNames =
      parentsWithSelf
        .filterIsInstance<KtClassOrObject>()
        .map { it.nameAsSafeName.asString() }
        .toList()
        .reversed()
  )

internal fun ClassDescriptor.asClassName(): ClassName =
  ClassName(
    packageName =
      parents
        .filterIsInstance<PackageFragmentDescriptor>()
        .first()
        .fqName.safePackageString(dotSuffix = false),
    simpleNames =
      parentsWithSelf
        .filterIsInstance<ClassDescriptor>()
        .map { it.name.asString() }
        .toList()
        .reversed()
  )

internal fun KotlinType.asTypeName(): TypeName {
  return asTypeNameOrNull { true }!!
}

/**
 * @param rawTypeFilter an optional raw type filter to allow for
 * ```
 *                      short-circuiting this before attempting to
 *                      resolve type arguments.
 * ```
 */
internal fun KotlinType.asTypeNameOrNull(
  rawTypeFilter: (ClassName) -> Boolean = { true }
): TypeName? {
  if (isTypeParameter()) return TypeVariableName(toString())

  val className = requireClassDescriptor().asClassName()
  if (!rawTypeFilter(className)) {
    return null
  }
  if (arguments.isEmpty()) return className.copy(nullable = isMarkedNullable)

  val argumentTypeNames =
    arguments.map { typeProjection ->
      if (typeProjection.isStarProjection) {
        STAR
      } else {
        val typeName = typeProjection.type.asTypeName()
        when (typeProjection.projectionKind) {
          INVARIANT -> typeName
          OUT_VARIANCE -> WildcardTypeName.producerOf(typeName)
          IN_VARIANCE -> WildcardTypeName.consumerOf(typeName)
        }
      }
    }

  return className.parameterizedBy(argumentTypeNames).copy(nullable = isMarkedNullable)
}
