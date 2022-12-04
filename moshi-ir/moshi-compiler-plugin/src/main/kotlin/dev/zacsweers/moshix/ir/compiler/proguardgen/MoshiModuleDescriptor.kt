package dev.zacsweers.moshix.ir.compiler.proguardgen

import com.squareup.anvil.compiler.internal.classIdBestGuess
import com.squareup.anvil.compiler.internal.reference.AnvilModuleDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClassOrObject

internal interface MoshiModuleDescriptor : AnvilModuleDescriptor {
  companion object {
    operator fun invoke(delegate: ModuleDescriptor): ModuleDescriptor =
      RealMoshiModuleDescriptor.Factory().create(delegate)
  }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun ModuleDescriptor.asAnvilModuleDescriptor(): MoshiModuleDescriptor =
  this as MoshiModuleDescriptor

internal fun ModuleDescriptor.resolveFqNameOrNull(fqName: FqName): FqName? =
  asAnvilModuleDescriptor().resolveClassIdOrNull(fqName.classIdBestGuess())

internal fun ModuleDescriptor.getKtClassOrObjectOrNull(fqName: FqName): KtClassOrObject? =
  asAnvilModuleDescriptor().getKtClassOrObjectOrNull(fqName)

internal fun ModuleDescriptor.canResolveFqName(fqName: FqName): Boolean =
  resolveFqNameOrNull(fqName) != null

internal fun ModuleDescriptor.resolveFqNameOrNull(packageName: FqName, className: String): FqName? =
  asAnvilModuleDescriptor().resolveClassIdOrNull(ClassId(packageName, Name.identifier(className)))

internal fun ModuleDescriptor.canResolveFqName(packageName: FqName, className: String): Boolean =
  resolveFqNameOrNull(packageName, className) != null
