package dev.zacsweers.moshix.sealed.codegen.ksp

import com.google.devtools.ksp.processing.Resolver
import com.squareup.moshi.JsonClass
import dev.zacsweers.moshix.sealed.annotations.DefaultNull
import dev.zacsweers.moshix.sealed.annotations.DefaultObject
import dev.zacsweers.moshix.sealed.annotations.NestedSealed
import dev.zacsweers.moshix.sealed.annotations.TypeLabel

internal class MoshiSealedSymbols(resolver: Resolver) {
  val jsonClass = resolver.getClassDeclarationByName<JsonClass>().asType()
  val defaultNull = resolver.getClassDeclarationByName<DefaultNull>().asType()
  val defaultObject = resolver.getClassDeclarationByName<DefaultObject>().asType()
  val typeLabel = resolver.getClassDeclarationByName<TypeLabel>().asType()
  val nestedSealed = resolver.getClassDeclarationByName<NestedSealed>().asType()
}