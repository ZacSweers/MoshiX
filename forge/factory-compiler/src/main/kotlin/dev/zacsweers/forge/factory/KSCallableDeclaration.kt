package dev.zacsweers.forge.factory

import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueParameter

// https://kotlinlang.slack.com/archives/C013BA8EQSE/p1606685684116000
internal interface KSCallableDeclaration {
  val qualifiedName: KSName?
  val simpleName: KSName
  val parameters: List<KSValueParameter>
  val type: KSTypeReference
  companion object {
    fun create(declaration: KSDeclaration): KSCallableDeclaration? {
      return when (declaration) {
        is KSFunctionDeclaration -> ForgeKSFunctionDeclaration(declaration)
        is KSPropertyDeclaration -> ForgeKSPropertyDeclaration(declaration)
        else -> null
      }
    }
  }
}
internal class ForgeKSFunctionDeclaration(delegate: KSFunctionDeclaration) : KSCallableDeclaration, KSFunctionDeclaration by delegate {
  override val type: KSTypeReference
    get() = returnType!!
}
internal class ForgeKSPropertyDeclaration(delegate: KSPropertyDeclaration) : KSCallableDeclaration, KSPropertyDeclaration by delegate {
  override val parameters: List<KSValueParameter> = emptyList()
}
