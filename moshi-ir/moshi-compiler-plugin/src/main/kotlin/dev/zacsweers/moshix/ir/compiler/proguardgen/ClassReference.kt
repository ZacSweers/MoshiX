package dev.zacsweers.moshix.ir.compiler.proguardgen

import dev.zacsweers.moshix.ir.compiler.proguardgen.ClassReference.Descriptor
import dev.zacsweers.moshix.ir.compiler.proguardgen.ClassReference.Psi
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

/**
 * Used to create a common type between [KtClassOrObject] class references and [ClassDescriptor]
 * references, to streamline parsing.
 *
 * @see allSuperTypeClassReferences
 * @see requireClassReference
 */
internal sealed class ClassReference {

  abstract val fqName: FqName

  class Psi internal constructor(val clazz: KtClassOrObject, override val fqName: FqName) :
    ClassReference()

  class Descriptor internal constructor(val clazz: ClassDescriptor, override val fqName: FqName) :
    ClassReference()

  override fun toString(): String {
    return "${this::class.qualifiedName}($fqName)"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ClassReference) return false

    if (fqName != other.fqName) return false

    return true
  }

  override fun hashCode(): Int {
    return fqName.hashCode()
  }
}

/**
 * Attempts to resolve the [fqName] to a [ClassDescriptor] first, then falls back to a
 * [KtClassOrObject] if the descriptor resolution fails. This will happen if the code being parsed
 * was generated as part of the compilation round for this module.
 */
internal fun ModuleDescriptor.classReferenceOrNull(fqName: FqName): ClassReference? {
  return fqName.classDescriptorOrNull(this)?.toClassReference()
    ?: getKtClassOrObjectOrNull(fqName)?.toClassReference()
}

internal fun ClassDescriptor.toClassReference(): Descriptor {
  return Descriptor(this, fqNameSafe)
}

internal fun KtClassOrObject.toClassReference(): Psi {
  return Psi(this, requireFqName())
}

internal fun ModuleDescriptor.requireClassReference(fqName: FqName): ClassReference {
  return classReferenceOrNull(fqName)
    ?: throw MoshiCompilationException("Couldn't resolve ClassReference for $fqName")
}

/**
 * This will return all super types as [ClassReference], whether they're parsed as [KtClassOrObject]
 * or [ClassDescriptor]. This will include generated code, assuming it has already been generated.
 * The returned sequence will be distinct by FqName, and Psi types are preferred over Descriptors.
 *
 * The first elements in the returned sequence represent the direct superclass to the receiver. The
 * last elements represent the types which are furthest up-stream.
 *
 * @param includeSelf If true, the receiver class is the first element of the sequence
 */
internal fun ClassReference.allSuperTypeClassReferences(
  module: ModuleDescriptor,
  includeSelf: Boolean = false
): Sequence<ClassReference> {
  return generateSequence(sequenceOf(this)) { superTypes ->
      superTypes
        .map { classRef -> classRef.directSuperClassReferences(module) }
        .flatten()
        .takeIf { it.firstOrNull() != null }
    }
    .drop(if (includeSelf) 0 else 1)
    .flatten()
    .distinctBy { it.fqName }
}

internal fun ClassReference.directSuperClassReferences(
  module: ModuleDescriptor
): Sequence<ClassReference> {
  return when (this) {
    is Descriptor ->
      clazz.directSuperClassAndInterfaces().asSequence().map {
        module.requireClassReference(it.fqNameSafe)
      }
    is Psi ->
      clazz.superTypeListEntries
        .asSequence()
        .map { it.requireFqName(module) }
        .map { module.requireClassReference(it) }
  }
}

/**
 * @param parameterName The name of the parameter to be found, not including any variance modifiers.
 * @return The 0-based index of a declared generic type.
 */
internal fun ClassReference.indexOfTypeParameter(parameterName: String): Int {
  return when (this) {
    is Descriptor ->
      clazz.declaredTypeParameters.indexOfFirst { it.name.asString() == parameterName }
    is Psi -> clazz.typeParameters.indexOfFirst { it.identifyingElement?.text == parameterName }
  }
}
