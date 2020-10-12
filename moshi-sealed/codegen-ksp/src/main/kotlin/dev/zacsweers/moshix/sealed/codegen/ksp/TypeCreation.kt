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
import javax.lang.model.element.TypeElement

internal data class Subtype(
  val className: TypeName,
  val isDefaultObject: Boolean,
  val typeLabelType: String?,
  val originatingElement: TypeElement?,
)

internal fun createType(
  targetType: ClassName,
  isInternal: Boolean,
  typeLabel: String,
  useDefaultNull: Boolean,
  originatingElement: TypeElement?,
  generatedAnnotation: AnnotationSpec?,
  subtypes: Set<Subtype>,
  objectAdapters: List<CodeBlock>
): FileSpec {
  val defaultCodeBlockBuilder = CodeBlock.builder()
  val adapterName = ClassName.bestGuess(
    Types.generatedJsonAdapterName(targetType.reflectionName())).simpleName
  val visibilityModifier = if (isInternal) KModifier.INTERNAL else KModifier.PUBLIC
  val allocator = NameAllocator()

  val moshiParam = ParameterSpec.builder(allocator.newName("moshi"), Moshi::class)
    .build()
  val jsonAdapterType = JsonAdapter::class.asClassName().parameterizedBy(targetType)

  val classBuilder = TypeSpec.classBuilder(adapterName)
    .addModifiers(visibilityModifier)
    .superclass(jsonAdapterType)
    .primaryConstructor(FunSpec.constructorBuilder().addParameter(moshiParam).build())

  originatingElement?.let(classBuilder::addOriginatingElement)

  generatedAnnotation?.let {
    classBuilder.addAnnotation(it)
  }

  val runtimeAdapterInitializer = CodeBlock.builder()
    .add("%T.of(%T::class.java, %S)«\n",
      PolymorphicJsonAdapterFactory::class,
      targetType,
      typeLabel
    )

  if (useDefaultNull) {
    defaultCodeBlockBuilder.add("null")
  }

  for (subtype in subtypes) {
    if (subtype.isDefaultObject) {
      defaultCodeBlockBuilder.add("%T", subtype.className)
      continue
    }
    subtype.originatingElement?.let(classBuilder::addOriginatingElement)
    runtimeAdapterInitializer.add("  .withSubtype(%T::class.java, %S)\n",
      subtype.className,
      subtype.typeLabelType
    )
  }

  if (defaultCodeBlockBuilder.isNotEmpty()) {
    runtimeAdapterInitializer.add("  .withDefaultValue(%L)\n", defaultCodeBlockBuilder.build())
  }

  val moshiArg = if (objectAdapters.isEmpty()) {
    CodeBlock.of("%N", moshiParam)
  } else {
    CodeBlock.builder()
      .add("%N.newBuilder()\n", moshiParam)
      .apply {
        add("%L\n", objectAdapters.joinToCode("\n", prefix = "    "))
      }
      .add(".build()")
      .build()
  }
  runtimeAdapterInitializer.add("  .create(%T::class.java, %M(), %L)·as·%T\n»",
    targetType,
    MemberName("kotlin.collections", "emptySet"),
    moshiArg,
    jsonAdapterType
  )

  val runtimeAdapterProperty = PropertySpec.builder(
    allocator.newName("runtimeAdapter"),
    jsonAdapterType,
    KModifier.PRIVATE
  )
    .addAnnotation(
      AnnotationSpec.builder(Suppress::class)
        .addMember("%S", "UNCHECKED_CAST")
        .build()
    )
    .initializer(runtimeAdapterInitializer.build())
    .build()

  val nullableTargetType = targetType.copy(nullable = true)
  val readerParam = ParameterSpec(allocator.newName("reader"), JsonReader::class.asClassName())
  val writerParam = ParameterSpec(allocator.newName("writer"), JsonWriter::class.asClassName())
  val valueParam = ParameterSpec(allocator.newName("value"), nullableTargetType)
  classBuilder.addProperty(runtimeAdapterProperty)
    .addFunction(FunSpec.builder("fromJson")
      .addModifiers(KModifier.OVERRIDE)
      .addParameter(readerParam)
      .returns(nullableTargetType)
      .addStatement("return %N.fromJson(%N)", runtimeAdapterProperty, readerParam)
      .build())
    .addFunction(FunSpec.builder("toJson")
      .addModifiers(KModifier.OVERRIDE)
      .addParameter(writerParam)
      .addParameter(valueParam)
      .addStatement("%N.toJson(%N, %N)", runtimeAdapterProperty, writerParam, valueParam)
      .build())

  // TODO how do generics work?
  return FileSpec.builder(targetType.packageName, adapterName)
    .indent("  ")
    .addComment("Code generated by moshi-sealed. Do not edit.")
    .addType(classBuilder.build())
    .build()
}