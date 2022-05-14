package dev.zacsweers.moshix.ir.compiler.proguardgen

import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.findTypeAliasAcrossModuleDependencies
import org.jetbrains.kotlin.descriptors.resolveClassByFqName
import org.jetbrains.kotlin.incremental.components.NoLookupLocation.FROM_BACKEND
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

internal class RealMoshiModuleDescriptor(delegate: ModuleDescriptor) :
  MoshiModuleDescriptor, ModuleDescriptor by delegate {

  private val classesMap = mutableMapOf<String, List<KtClassOrObject>>()
  private val allClasses: Sequence<KtClassOrObject>
    get() = classesMap.values.asSequence().flatMap { it }

  override fun resolveClassIdOrNull(classId: ClassId): FqName? {
    val fqName = classId.asSingleFqName()

    resolveClassByFqName(fqName, FROM_BACKEND)?.let {
      return it.fqNameSafe
    }

    findTypeAliasAcrossModuleDependencies(classId)?.let {
      return it.fqNameSafe
    }

    return allClasses.firstOrNull { it.fqName == fqName }?.fqName
  }

  override fun getKtClassOrObjectOrNull(fqName: FqName): KtClassOrObject? {
    return allClasses.firstOrNull { it.fqName == fqName }
  }
}
