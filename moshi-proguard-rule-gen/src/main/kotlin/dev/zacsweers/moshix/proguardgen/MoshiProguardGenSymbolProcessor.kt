package dev.zacsweers.moshix.proguardgen

import com.google.auto.service.AutoService
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.codegen.api.InternalMoshiCodegenApi
import com.squareup.moshi.kotlin.codegen.api.ProguardConfig

public class MoshiProguardGenSymbolProcessor(private val environment: SymbolProcessorEnvironment) :
  SymbolProcessor {

  internal companion object {

    private val JSON_CLASS_FQ_NAME = JsonClass::class.qualifiedName!!
    private val MOSHI_REFLECTIVE_NAME = Moshi::class.asClassName().reflectionName()
    private val TYPE_ARRAY_REFLECTIVE_NAME =
      "${java.lang.reflect.Type::class.asClassName().reflectionName()}[]"
    private const val NESTED_SEALED_FQ_NAME = "dev.zacsweers.moshix.sealed.annotations.NestedSealed"

    /**
     * This boolean processing option can control sealed proguard rule generation. Normally, this is
     * not recommended unless end-users build their own JsonAdapter look-up tool. This is enabled by
     * default.
     */
    const val OPTION_GENERATE_PROGUARD_RULES: String = "moshi.generateProguardRules"

    /**
     * This boolean processing option can control proguard rule generation. Normally, this is not
     * recommended unless end-users build their own JsonAdapter look-up tool. This is disabled by
     * default, and should only be used if using moshi-ir.
     */
    const val OPTION_GENERATE_MOSHI_CORE_PROGUARD_RULES: String =
      "moshi.generateCoreMoshiProguardRules"
  }

  // Off by default, only used by moshi-ir
  private val enableMoshi: Boolean =
    environment.options[OPTION_GENERATE_MOSHI_CORE_PROGUARD_RULES]?.toBoolean() ?: false

  // Ony by default, used by moshi-ir and moshi-sealed
  private val enableSealed: Boolean =
    environment.options[OPTION_GENERATE_PROGUARD_RULES]?.toBoolean() ?: true

  @OptIn(KspExperimental::class, InternalMoshiCodegenApi::class)
  override fun process(resolver: Resolver): List<KSAnnotated> {
    resolver
      .getSymbolsWithAnnotation(JSON_CLASS_FQ_NAME)
      .mapNotNull {
        if (it !is KSClassDeclaration) {
          // Don't worry about erroring here, the real generator will do it
          return@mapNotNull null
        }
        it to it.getAnnotationsByType(JsonClass::class).single()
      }
      .filter { (_, annotation) -> annotation.generateAdapter }
      .forEach { (clazz, jsonClass) ->
        val generatorKey = jsonClass.generator
        val isMoshiSealed = (enableSealed && generatorKey.startsWith("sealed:"))

        if ((enableMoshi && generatorKey.isEmpty()) || isMoshiSealed) {
          val targetType = clazz.toClassName()
          val hasGenerics = clazz.typeParameters.isNotEmpty()
          val adapterName = "${targetType.simpleNames.joinToString(separator = "_")}JsonAdapter"
          val adapterConstructorParams =
            when (hasGenerics) {
              false -> listOf(MOSHI_REFLECTIVE_NAME)
              true -> listOf(MOSHI_REFLECTIVE_NAME, TYPE_ARRAY_REFLECTIVE_NAME)
            }

          val nestedSealedClassNames: Set<ClassName>
          if (isMoshiSealed && clazz.isSealed) {
            nestedSealedClassNames = mutableSetOf()
            // Skip initial annotation check because this is top level
            clazz.walkSealedSubtypes(nestedSealedClassNames, skipAnnotationCheck = true)
          } else {
            nestedSealedClassNames = emptySet()
          }

          val config =
            ProguardConfig(
              targetClass = targetType,
              adapterName = adapterName,
              adapterConstructorParams = adapterConstructorParams,
              // Not actually true but in our case we don't need the generated rules for htis
              targetConstructorHasDefaults = false,
              targetConstructorParams = emptyList(),
            )
          environment.logger.info(
            "MOSHI: Writing proguard rules for ${targetType.canonicalName}: $config",
            clazz,
          )
          val fileName = config.outputFilePathWithoutExtension(targetType.canonicalName)
          environment.codeGenerator
            .createNewFile(
              Dependencies(
                false,
                sources = clazz.containingFile?.let { arrayOf(it) } ?: emptyArray(),
              ),
              packageName = "",
              fileName = fileName,
              extensionName = "pro",
            )
            .bufferedWriter()
            .use { writer ->
              if (enableMoshi) {
                config.writeTo(writer)
              }
              if (nestedSealedClassNames.isNotEmpty()) {
                // Add a note for reference
                writer.appendLine(
                  "\n# Conditionally keep this adapter for every possible nested subtype that uses it."
                )
                val adapterCanonicalName =
                  ClassName(targetType.packageName, adapterName).canonicalName
                for (target in nestedSealedClassNames.sorted()) {
                  writer.appendLine("-if class $target")
                  writer.appendLine("-keep class $adapterCanonicalName {")
                  // Keep the constructor for Moshi's reflective lookup
                  val constructorArgs = adapterConstructorParams.joinToString(",")
                  writer.appendLine("    public <init>($constructorArgs);")
                  writer.appendLine("}")
                }
              }
            }
        }
      }
    return emptyList()
  }

  private val KSClassDeclaration.isSealed: Boolean
    get() = Modifier.SEALED in modifiers

  private fun KSClassDeclaration.walkSealedSubtypes(
    elements: MutableSet<ClassName>,
    skipAnnotationCheck: Boolean,
  ) {
    if (isSealed) {
      if (!skipAnnotationCheck) {
        if (
          annotations.any {
            it.annotationType.resolve().declaration.qualifiedName?.asString() ==
              NESTED_SEALED_FQ_NAME
          }
        ) {
          elements += toClassName()
        } else {
          return
        }
      }
      for (nested in getSealedSubclasses()) {
        nested.walkSealedSubtypes(elements, skipAnnotationCheck = false)
      }
    } else {
      elements += toClassName()
    }
  }

  @AutoService(SymbolProcessorProvider::class)
  public class Provider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
      MoshiProguardGenSymbolProcessor(environment)
  }
}
