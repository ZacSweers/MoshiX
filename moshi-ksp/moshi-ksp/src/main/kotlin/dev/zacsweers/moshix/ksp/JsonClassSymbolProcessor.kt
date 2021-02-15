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
package dev.zacsweers.moshix.ksp

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.impl.MessageCollectorBasedKSPLogger
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.Origin.KOTLIN
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.moshi.JsonClass
import dev.zacsweers.moshix.ksp.shade.api.AdapterGenerator
import dev.zacsweers.moshix.ksp.shade.api.ProguardConfig
import dev.zacsweers.moshix.ksp.shade.api.PropertyGenerator
import org.jetbrains.kotlin.analyzer.AnalysisResult.CompilationErrorException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

@AutoService(SymbolProcessor::class)
public class JsonClassSymbolProcessor : SymbolProcessor {

  public companion object {
    /**
     * This annotation processing argument can be specified to have a `@Generated` annotation
     * included in the generated code. It is not encouraged unless you need it for static analysis
     * reasons and not enabled by default.
     *
     * Note that this can only be one of the following values:
     *   * `"javax.annotation.processing.Generated"` (JRE 9+)
     *   * `"javax.annotation.Generated"` (JRE <9)
     */
    public const val OPTION_GENERATED: String = "moshi.generated"

    private val POSSIBLE_GENERATED_NAMES = setOf(
      "javax.annotation.processing.Generated",
      "javax.annotation.Generated"
    )

    private val JSON_CLASS_NAME = JsonClass::class.qualifiedName!!
  }

  private lateinit var codeGenerator: CodeGenerator
  private lateinit var logger: KSPLogger
  private var generatedOption: String? = null

  override fun init(
    options: Map<String, String>,
    kotlinVersion: KotlinVersion,
    codeGenerator: CodeGenerator,
    logger: KSPLogger,
  ) {
    this.codeGenerator = codeGenerator
    this.logger = logger

    generatedOption = options[OPTION_GENERATED]?.also {
      logger.check(it in POSSIBLE_GENERATED_NAMES) {
        "Invalid option value for $OPTION_GENERATED. Found $it, allowable values are $POSSIBLE_GENERATED_NAMES."
      }
    }
  }

  override fun process(resolver: Resolver): List<KSAnnotated> {
    val generatedAnnotation = generatedOption?.let {
      val annotationType = resolver.getClassDeclarationByName(resolver.getKSNameFromString(it))
        ?: run {
          logger.error("Generated annotation type doesn't exist: $it")
          return emptyList()
        }
      AnnotationSpec.builder(annotationType.toClassName())
        .addMember("value = [%S]", JsonClassSymbolProcessor::class.java.canonicalName)
        .addMember("comments = %S", "https://github.com/square/moshi")
        .build()
    }

    val jsonClassType = resolver.getClassDeclarationByName(
      resolver.getKSNameFromString(JSON_CLASS_NAME)
    )
      ?.asType()
      ?: run {
        logger.error("JsonClass type not found on the classpath.")
        return emptyList()
      }
    resolver.getSymbolsWithAnnotation(JSON_CLASS_NAME)
      .asSequence()
      .forEach { type ->
        logger.check(type is KSClassDeclaration && type.origin == KOTLIN, type) {
          "@JsonClass can't be applied to $type: must be a Kotlin class"
        }
        // For the smart cast
        if (type !is KSClassDeclaration) return@forEach

        val jsonClassAnnotation = type.findAnnotationWithType(jsonClassType) ?: return@forEach

        val generator = jsonClassAnnotation.getMember<String>("generator")

        if (generator.isNotEmpty()) return@forEach

        if (!jsonClassAnnotation.getMember<Boolean>("generateAdapter")) return@forEach

        val originatingFile = type.containingFile!!
        val adapterGenerator = adapterGenerator(logger, resolver, type) ?: return emptyList()
        try {
          val preparedAdapter = adapterGenerator
            .prepare { spec ->
              spec.toBuilder()
                .apply {
                  generatedAnnotation?.let(::addAnnotation)
                }
                .addOriginatingKSFile(originatingFile)
                .build()
            }
          preparedAdapter.spec.writeTo(codeGenerator)
          preparedAdapter.proguardConfig?.writeTo(codeGenerator, originatingFile)
        } catch (e: Exception) {
          logger.error(
            "Error preparing ${type.simpleName.asString()}: ${e.stackTrace.joinToString("\n")}"
          )
        }
      }
    return emptyList()
  }

  override fun finish() {
  }

  private fun ProguardConfig.writeTo(codeGenerator: CodeGenerator, originatingKSFile: KSFile) {
    // TODO outputFile needs to be public
    val name = "META-INF/proguard/moshi-${targetClass.canonicalName}"
    val file = codeGenerator.createNewFile(
      Dependencies(false, originatingKSFile),
      "",
      name,
      "pro"
    )
    // Don't use writeTo(file) because that tries to handle directories under the hood
    OutputStreamWriter(file, StandardCharsets.UTF_8)
      .use {
        // TODO writeTo(Appendable) needs to be public
        ProguardConfig::class.java.getDeclaredMethod("writeTo", Appendable::class.java)
          .apply {
            isAccessible = true
          }
          .invoke(this, it)
      }
  }

  private fun adapterGenerator(
    logger: KSPLogger,
    resolver: Resolver,
    originalType: KSClassDeclaration,
  ): AdapterGenerator? {
    val type = targetType(originalType, resolver, logger) ?: return null

    val properties = mutableMapOf<String, PropertyGenerator>()
    for (property in type.properties.values) {
      val generator = property.generator(logger, resolver, originalType)
      if (generator != null) {
        properties[property.name] = generator
      }
    }

    for ((name, parameter) in type.constructor.parameters) {
      if (type.properties[parameter.name] == null && !parameter.hasDefault) {
        // TODO would be nice if we could pass the parameter node directly?
        logger.error("No property for required constructor parameter $name", originalType)
        return null
      }
    }

    // Sort properties so that those with constructor parameters come first.
    val sortedProperties = properties.values.sortedBy {
      if (it.hasConstructorParameter) {
        it.target.parameterIndex
      } else {
        Integer.MAX_VALUE
      }
    }

    return AdapterGenerator(type, sortedProperties)
  }

  override fun onError() {
    // TODO temporary until KSP's logger makes errors fail the compilation and not just the build
    (logger as? MessageCollectorBasedKSPLogger)?.reportAll()
    throw CompilationErrorException()
  }
}
