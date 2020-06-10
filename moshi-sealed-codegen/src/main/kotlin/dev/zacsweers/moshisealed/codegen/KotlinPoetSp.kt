package dev.zacsweers.moshisealed.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import org.jetbrains.kotlin.ksp.symbol.KSClassifierReference
import org.jetbrains.kotlin.ksp.symbol.KSType
import org.jetbrains.kotlin.ksp.symbol.KSTypeArgument
import org.jetbrains.kotlin.ksp.symbol.KSTypeReference

//fun KSType.asTypeName(): TypeName {
//
//}
//
//fun KSClassifierReference.asTypeName(): TypeName {
//  val rawType = ClassName.bestGuess(referencedName())
//  val fullType = rawType.parameterizedBy(typeArguments)
//}
//
//fun ClassName.parameterizedBy(ksTypes: List<KSTypeArgument>): ParameterizedTypeName {
//  if (ksTypes.isEmpty()) return parameterizedBy()
//
//
//}
//
//fun KSTypeArgument.asTypeName(): TypeVariableName {
//
//}
//
//fun KSTypeReference.asTypeName(): TypeName {
//
//}