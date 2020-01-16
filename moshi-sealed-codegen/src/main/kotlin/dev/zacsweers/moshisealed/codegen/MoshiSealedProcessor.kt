/*
 * Copyright (c) 2019 Zac Sweers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.zacsweers.moshisealed.codegen

import com.google.auto.service.AutoService
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
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.metadata.ImmutableKmClass
import com.squareup.kotlinpoet.metadata.KotlinPoetMetadataPreview
import com.squareup.kotlinpoet.metadata.isInternal
import com.squareup.kotlinpoet.metadata.isObject
import com.squareup.kotlinpoet.metadata.isSealed
import com.squareup.kotlinpoet.metadata.toImmutableKmClass
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types.generatedJsonAdapterName
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import dev.zacsweers.moshisealed.annotations.DefaultNull
import dev.zacsweers.moshisealed.annotations.DefaultObject
import dev.zacsweers.moshisealed.annotations.TypeLabel
import dev.zacsweers.moshisealed.codegen.MoshiSealedProcessor.Companion.OPTION_GENERATED
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor
import net.ltgt.gradle.incap.IncrementalAnnotationProcessorType
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Filer
import javax.annotation.processing.Messager
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedOptions
import javax.lang.model.SourceVersion
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import javax.tools.Diagnostic

@KotlinPoetMetadataPreview
@IncrementalAnnotationProcessor(IncrementalAnnotationProcessorType.AGGREGATING)
@SupportedOptions(OPTION_GENERATED)
@AutoService(Processor::class)
class MoshiSealedProcessor : AbstractProcessor() {

  companion object {
    /**
     * This annotation processing argument can be specified to have a `@Generated` annotation
     * included in the generated code. It is not encouraged unless you need it for static analysis
     * reasons and not enabled by default.
     *
     * Note that this can only be one of the following values:
     *   * `"javax.annotation.processing.Generated"` (JRE 9+)
     *   * `"javax.annotation.Generated"` (JRE <9)
     *
     * We reuse Moshi's option for convenience so you don't have to declare multiple options.
     */
    const val OPTION_GENERATED = "moshi.generated"
    private val POSSIBLE_GENERATED_NAMES = setOf(
        "javax.annotation.processing.Generated",
        "javax.annotation.Generated"
    )
  }

  private lateinit var filer: Filer
  private lateinit var messager: Messager
  private lateinit var elements: Elements
  private lateinit var types: Types
  private lateinit var options: Map<String, String>
  private var generatedAnnotation: AnnotationSpec? = null

  override fun init(processingEnv: ProcessingEnvironment) {
    super.init(processingEnv)
    filer = processingEnv.filer
    messager = processingEnv.messager
    elements = processingEnv.elementUtils
    types = processingEnv.typeUtils
    options = processingEnv.options
    generatedAnnotation = processingEnv.options[OPTION_GENERATED]?.let {
      require(it in POSSIBLE_GENERATED_NAMES) {
        "Invalid option value for $OPTION_GENERATED. Found $it, allowable values are $POSSIBLE_GENERATED_NAMES."
      }
      elements.getTypeElement(it)
    }?.let {
      AnnotationSpec.builder(it.asClassName())
          .addMember("value = [%S]", MoshiSealedProcessor::class.java.canonicalName)
          .addMember("comments = %S", "https://github.com/ZacSweers/moshi-sealed")
          .build()
    }
  }

  override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

  override fun getSupportedAnnotationTypes(): Set<String> {
    return setOf(JsonClass::class, TypeLabel::class, DefaultObject::class, DefaultNull::class)
        .mapTo(mutableSetOf()) { it.java.canonicalName }
  }

  override fun process(annotations: Set<TypeElement>,
      roundEnv: RoundEnvironment): Boolean {

    roundEnv.getElementsAnnotatedWith(JsonClass::class.java)
        .asSequence()
        .map { it as TypeElement }
        .forEach { type ->
          val generator = type.getAnnotation(JsonClass::class.java).generator
          if (!generator.startsWith("sealed:")) {
            return@forEach
          }
          val typeLabel = generator.removePrefix("sealed:")
          val kmClass = type.getAnnotation(Metadata::class.java).toImmutableKmClass()
          if (!kmClass.isSealed) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Must be a sealed class!", type)
            return@forEach
          }
          createType(type, typeLabel, kmClass)
        }

    return false
  }

  private fun createType(element: TypeElement, typeLabel: String, kmClass: ImmutableKmClass) {
    val sealedSubtypes = kmClass.sealedSubclasses
        .map {
          // Canonicalize
          it.replace("/", ".")
        }
        .map { elements.getTypeElement(it) }
        .associateWithTo(LinkedHashMap()) {
          it.getAnnotation(Metadata::class.java).toImmutableKmClass()
        }
    val defaultCodeBlockBuilder = CodeBlock.builder()
    val adapterName = ClassName.bestGuess(generatedJsonAdapterName(element.asClassName().reflectionName())).simpleName
    val visibilityModifier = if (element.toImmutableKmClass().flags.isInternal) KModifier.INTERNAL else KModifier.PUBLIC
    val allocator = NameAllocator()

    val targetType = element.asClassName()
    val moshiParam = ParameterSpec.builder(allocator.newName("moshi"), Moshi::class)
        .build()
    val jsonAdapterType = JsonAdapter::class.asClassName().parameterizedBy(targetType)

    val classBuilder = TypeSpec.classBuilder(adapterName)
        .addModifiers(visibilityModifier)
        .superclass(jsonAdapterType)
        .primaryConstructor(FunSpec.constructorBuilder().addParameter(moshiParam).build())
        .addOriginatingElement(element)

    generatedAnnotation?.let {
      classBuilder.addAnnotation(it)
    }

    val runtimeAdapterInitializer = CodeBlock.builder()
        .add("%T.of(%T::class.java, %S)«\n",
            PolymorphicJsonAdapterFactory::class,
            targetType,
            typeLabel
        )

    val useDefaultNull = element.getAnnotation(DefaultNull::class.java) != null
    if (useDefaultNull) {
      defaultCodeBlockBuilder.add("null")
    }

    for ((type, kmData) in sealedSubtypes) {
      if (kmData.isObject) {
        val isDefaultObject = type.getAnnotation(DefaultObject::class.java) != null
        if (isDefaultObject) {
          if (useDefaultNull) {
            // Print both for reference
            messager.printMessage(Diagnostic.Kind.ERROR, "Cannot have both @DefaultNull and @DefaultObject. @DefaultObject type.", type)
            messager.printMessage(Diagnostic.Kind.ERROR, "Cannot have both @DefaultNull and @DefaultObject. @DefaultNull type.", element)
            return
          } else {
            defaultCodeBlockBuilder.add("%T", type)
          }
        } else if (!useDefaultNull) {
          messager.printMessage(Diagnostic.Kind.ERROR, "Unhandled object type, cannot serialize this.", type)
          return
        }
        continue
      }
      classBuilder.addOriginatingElement(type)
      val labelAnnotation = type.getAnnotation(TypeLabel::class.java) ?: run {
        messager.printMessage(Diagnostic.Kind.ERROR, "Missing @TypeLabel.", type)
        return
      }
      runtimeAdapterInitializer.add("    .withSubtype(%T::class.java, %S)\n",
          type.asClassName(),
          labelAnnotation.value
      )
    }

    if (defaultCodeBlockBuilder.isNotEmpty()) {
      runtimeAdapterInitializer.add("    .withDefaultValue(%L)\n", defaultCodeBlockBuilder.build())
    }

    runtimeAdapterInitializer.add("    .create(%T::class.java, %M(), %N)·as·%T\n»",
        targetType,
        MemberName("kotlin.collections", "emptySet"),
        moshiParam,
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
    FileSpec.builder(targetType.packageName, adapterName)
        .indent("  ")
        .addComment("Code generated by moshi-sealed. Do not edit.")
        .addType(classBuilder.build())
        .build()
        .writeTo(filer)
  }
}
