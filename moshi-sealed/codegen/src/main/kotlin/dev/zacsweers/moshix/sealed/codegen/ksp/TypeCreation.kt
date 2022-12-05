/*
 * Copyright (C) 2021 Zac Sweers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.zacsweers.moshix.sealed.codegen.ksp

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.joinToCode
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import dev.zacsweers.moshix.sealed.codegen.PreparedAdapter
import dev.zacsweers.moshix.sealed.codegen.ProguardConfig
import dev.zacsweers.moshix.sealed.codegen.ksp.Subtype.ClassType
import dev.zacsweers.moshix.sealed.codegen.ksp.Subtype.ObjectType

internal sealed class Subtype(val className: TypeName) {
  class ObjectType(className: TypeName) : Subtype(className)
  class ClassType(className: TypeName, val labels: List<String>) : Subtype(className)
}

internal fun createType(
  targetType: ClassName,
  isInternal: Boolean,
  labelKey: String,
  fallbackStrategy: FallbackStrategy?,
  generatedAnnotation: AnnotationSpec?,
  nestedSealedClassNames: Set<ClassName>,
  subtypes: Set<Subtype>,
  objectAdapters: List<CodeBlock>,
  generateProguardConfig: Boolean,
  errorLogger: (String) -> Unit,
  typeSpecHook: TypeSpec.Builder.() -> Unit
): PreparedAdapter? {
  var finalFallbackStrategy = fallbackStrategy
  val adapterName =
    ClassName.bestGuess(Types.generatedJsonAdapterName(targetType.reflectionName())).simpleName
  val visibilityModifier = if (isInternal) KModifier.INTERNAL else KModifier.PUBLIC
  val allocator = NameAllocator()

  val moshiParam = ParameterSpec.builder(allocator.newName("moshi"), Moshi::class).build()
  val jsonAdapterType = JsonAdapter::class.asClassName().parameterizedBy(targetType)

  val classBuilder =
    TypeSpec.classBuilder(adapterName)
      .addModifiers(visibilityModifier)
      .superclass(jsonAdapterType)
      .primaryConstructor(FunSpec.constructorBuilder().addParameter(moshiParam).build())

  classBuilder.typeSpecHook()

  generatedAnnotation?.let { classBuilder.addAnnotation(it) }

  var intermediateMoshiInit: CodeBlock? = null
  var moshiArg = CodeBlock.of("%N", moshiParam)
  if (objectAdapters.isEmpty()) {
    CodeBlock.of("%N", moshiParam)
  } else {
    val moshiPropName = allocator.newName("moshi")
    moshiArg = CodeBlock.of("%L", moshiPropName)
    intermediateMoshiInit =
      CodeBlock.builder()
        .add("%N.newBuilder()\n", moshiParam)
        .apply { add("%L\n", objectAdapters.joinToCode("\n", prefix = "    ")) }
        .add(".build()")
        .build()
  }

  val runtimeAdapterInitializer =
    CodeBlock.builder()
      .add(
        "%T.of(%T::class.java, %S)\n",
        PolymorphicJsonAdapterFactory::class,
        targetType,
        labelKey
      )

  for (subtype in subtypes) {
    when (subtype) {
      is ObjectType -> {
        if (finalFallbackStrategy == null) {
          finalFallbackStrategy = FallbackStrategy.DefaultObject(subtype.className)
        } else {
          errorLogger(
            "Only one of @DefaultObject, @DefaultNull, or @FallbackJsonAdapter can be used at a time."
          )
          return null
        }
      }
      is ClassType -> {
        for (label in subtype.labels) {
          runtimeAdapterInitializer.add(
            "  .withSubtype(%T::class.java, %S)\n",
            subtype.className,
            label
          )
        }
      }
    }
  }

  finalFallbackStrategy?.let { runtimeAdapterInitializer.add("  %L\n", it.statement(moshiArg)) }
  runtimeAdapterInitializer.add(
    "  .create(%T::class.java, %M(), %L)·as·%T",
    targetType,
    MemberName("kotlin.collections", "emptySet"),
    moshiArg,
    jsonAdapterType
  )

  val runtimeAdapterProperty =
    PropertySpec.builder(allocator.newName("runtimeAdapter"), jsonAdapterType, KModifier.PRIVATE)
      .apply {
        if (intermediateMoshiInit == null) {
          // Initialize inline
          addAnnotation(suppressUncheckedCastAnnotation())
          if (objectAdapters.isNotEmpty()) {
            addAnnotation(optInExperimentalApiAnnotation())
          }
          initializer(runtimeAdapterInitializer.build())
        }
      }
      .build()

  // Must add this first!
  classBuilder.addProperty(runtimeAdapterProperty)

  if (intermediateMoshiInit != null) {
    // Need to do a custom init block!
    classBuilder.addInitializerBlock(
      CodeBlock.builder()
        .addStatement("val·%L·=·%L", moshiArg, intermediateMoshiInit)
        .addStatement("%L", suppressUncheckedCastAnnotation())
        .apply {
          if (objectAdapters.isNotEmpty()) {
            addStatement("%L", optInExperimentalApiAnnotation())
          }
        }
        .addStatement("%N·=·%L", runtimeAdapterProperty, runtimeAdapterInitializer.build())
        .build()
    )
  }

  val nullableTargetType = targetType.copy(nullable = true)
  val readerParam = ParameterSpec(allocator.newName("reader"), JsonReader::class.asClassName())
  val writerParam = ParameterSpec(allocator.newName("writer"), JsonWriter::class.asClassName())
  val valueParam = ParameterSpec(allocator.newName("value"), nullableTargetType)
  classBuilder
    .addFunction(
      FunSpec.builder("fromJson")
        .addModifiers(KModifier.OVERRIDE)
        .addParameter(readerParam)
        .returns(nullableTargetType)
        .addStatement("return %N.fromJson(%N)", runtimeAdapterProperty, readerParam)
        .build()
    )
    .addFunction(
      FunSpec.builder("toJson")
        .addModifiers(KModifier.OVERRIDE)
        .addParameter(writerParam)
        .addParameter(valueParam)
        .addStatement("%N.toJson(%N, %N)", runtimeAdapterProperty, writerParam, valueParam)
        .build()
    )

  // TODO how do generics work?
  val fileSpec =
    FileSpec.builder(targetType.packageName, adapterName)
      .indent("  ")
      .addFileComment("Code generated by moshi-sealed. Do not edit.")
      .addType(classBuilder.build())
      .build()

  val proguardConfig =
    if (generateProguardConfig) {
      ProguardConfig(
        targetClass = targetType,
        adapterName = adapterName,
        adapterConstructorParams = listOf(Moshi::class.asClassName().reflectionName()),
        nestedSubtypes = nestedSealedClassNames.sorted()
      )
    } else {
      null
    }

  return PreparedAdapter(fileSpec, proguardConfig)
}

private fun suppressUncheckedCastAnnotation(): AnnotationSpec =
  AnnotationSpec.builder(Suppress::class).addMember("%S", "UNCHECKED_CAST").build()

private fun optInExperimentalApiAnnotation(): AnnotationSpec =
  AnnotationSpec.builder(ClassName("kotlin", "OptIn"))
    .addMember("%T::class", ClassName("kotlin", "ExperimentalStdlibApi"))
    .build()
