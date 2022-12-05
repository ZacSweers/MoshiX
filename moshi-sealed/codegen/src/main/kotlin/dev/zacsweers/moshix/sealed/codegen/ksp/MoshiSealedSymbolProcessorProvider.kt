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

import com.google.auto.service.AutoService
import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.isVisibleFrom
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind.OBJECT
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import dev.zacsweers.moshix.sealed.codegen.ProguardConfig
import dev.zacsweers.moshix.sealed.codegen.ksp.MoshiSealedSymbolProcessorProvider.Companion.OPTION_GENERATED
import dev.zacsweers.moshix.sealed.codegen.ksp.MoshiSealedSymbolProcessorProvider.Companion.OPTION_GENERATE_PROGUARD_RULES
import dev.zacsweers.moshix.sealed.runtime.internal.ObjectJsonAdapter

@AutoService(SymbolProcessorProvider::class)
public class MoshiSealedSymbolProcessorProvider : SymbolProcessorProvider {
  public companion object {
    /**
     * This annotation processing argument can be specified to have a `@Generated` annotation
     * included in the generated code. It is not encouraged unless you need it for static analysis
     * reasons and not enabled by default.
     *
     * Note that this can only be one of the following values:
     * * `"javax.annotation.processing.Generated"` (JRE 9+)
     * * `"javax.annotation.Generated"` (JRE <9)
     *
     * We reuse Moshi's option for convenience so you don't have to declare multiple options.
     */
    public const val OPTION_GENERATED: String = "moshi.generated"

    /**
     * This boolean processing option can control proguard rule generation. Normally, this is not
     * recommended unless end-users build their own JsonAdapter look-up tool. This is enabled by
     * default.
     */
    public const val OPTION_GENERATE_PROGUARD_RULES: String = "moshi.generateProguardRules"
  }

  override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
    return MoshiSealedSymbolProcessor(environment)
  }
}

private class MoshiSealedSymbolProcessor(environment: SymbolProcessorEnvironment) :
  SymbolProcessor {

  private companion object {
    private val POSSIBLE_GENERATED_NAMES =
      setOf("javax.annotation.processing.Generated", "javax.annotation.Generated")

    private val JSON_CLASS_NAME = JsonClass::class.qualifiedName!!

    private val COMMON_SUPPRESS =
      arrayOf(
          // https://github.com/square/moshi/issues/1023
          "DEPRECATION",
          // Because we look it up reflectively
          "unused",
          // Because we include underscores
          "ClassName",
          // Because we generate redundant `out` variance for some generics and there's no way
          // for us to know when it's redundant.
          "REDUNDANT_PROJECTION",
          // Because we may generate redundant explicit types for local vars with default
          // values.
          // Example: 'var fooSet: Boolean = false'
          "RedundantExplicitType",
          // NameAllocator will just add underscores to differentiate names, which Kotlin
          // doesn't
          // like for stylistic reasons.
          "LocalVariableName",
          // KotlinPoet always generates explicit public modifiers for public members.
          "RedundantVisibilityModifier"
        )
        .let { suppressions ->
          AnnotationSpec.builder(Suppress::class)
            .addMember(suppressions.indices.joinToString { "%S" }, *suppressions)
            .build()
        }
  }

  private val codeGenerator = environment.codeGenerator
  private val logger = environment.logger
  private val generatedOption =
    environment.options[OPTION_GENERATED]?.also {
      require(it in POSSIBLE_GENERATED_NAMES) {
        "Invalid option value for $OPTION_GENERATED. Found $it, allowable values are $POSSIBLE_GENERATED_NAMES."
      }
    }
  private val generateProguardConfig =
    environment.options[OPTION_GENERATE_PROGUARD_RULES]?.toBooleanStrictOrNull() ?: true

  override fun process(resolver: Resolver): List<KSAnnotated> {
    val generatedAnnotation =
      generatedOption?.let {
        val annotationType =
          resolver.getClassDeclarationByName(resolver.getKSNameFromString(it))
            ?: run {
              logger.error("Generated annotation type doesn't exist: $it")
              return emptyList()
            }
        AnnotationSpec.builder(annotationType.toClassName())
          .addMember("value = [%S]", MoshiSealedSymbolProcessor::class.java.canonicalName)
          .addMember("comments = %S", "https://github.com/ZacSweers/moshi-sealed")
          .build()
      }

    val symbols = MoshiSealedSymbols(resolver)

    resolver.getSymbolsWithAnnotation(JSON_CLASS_NAME).asSequence().forEach { type ->
      check(type is KSClassDeclaration) { "@JsonClass is only applicable to classes!" }

      val labelKey = type.findAnnotationWithType(symbols.jsonClass)?.labelKey() ?: return@forEach

      check(Modifier.SEALED in type.modifiers) { "Must be a sealed class!" }

      // If this is a nested sealed type of a moshi-sealed parent, defer to the parent
      val sealedParent =
        if (type.hasAnnotation(symbols.nestedSealed)) {
          type.getAllSuperTypes().firstNotNullOfOrNull { supertype ->
            // Weird that we need to check the classifier ourselves
            supertype.declaration.findAnnotationWithType(symbols.jsonClass)?.labelKey()?.let {
              supertype to it
            }
          }
            ?: run {
              logger.error("No JsonClass-annotated sealed supertype found for $type", type)
              return@forEach
            }
        } else {
          null
        }

      sealedParent?.let { (_, parentLabelKey) ->
        if (parentLabelKey == labelKey) {
          logger.error(
            "@NestedSealed-annotated subtype $type is inappropriately annotated with @JsonClass(generator = " +
              "\"sealed:$labelKey\").",
            type
          )
          return@forEach
        }
      }

      createType(type, labelKey, generatedAnnotation, symbols)
    }

    return emptyList()
  }

  private fun KSAnnotation.labelKey(checkGenerateAdapter: Boolean = true): String? {
    if (checkGenerateAdapter && !getMember<Boolean>("generateAdapter")) {
      return null
    }

    val generator = getMember<String>("generator")

    if (!generator.startsWith("sealed:")) {
      return null
    }

    return generator.removePrefix("sealed:")
  }

  private fun createType(
    type: KSClassDeclaration,
    labelKey: String,
    generatedAnnotation: AnnotationSpec?,
    symbols: MoshiSealedSymbols,
  ) {
    val useDefaultNull = type.hasAnnotation(symbols.defaultNull)
    val fallbackAdapterAnnotation = type.findAnnotationWithType(symbols.fallbackJsonAdapter)
    if (useDefaultNull && (fallbackAdapterAnnotation != null)) {
      logger.error("Only one of @DefaultNull or @FallbackJsonAdapter can be used at a time", type)
      return
    }
    var fallbackStrategy: FallbackStrategy? = null
    if (fallbackAdapterAnnotation != null) {
      val adapterType = (fallbackAdapterAnnotation.arguments[0].value as KSType)
      // TODO can we check adapter type is valid? Compiler will check it for us
      val adapterDeclaration = adapterType.declaration as KSClassDeclaration
      val constructor = adapterDeclaration.primaryConstructor
      if (constructor?.isVisibleFrom(type) == false) {
        logger.error(
          "Fallback adapter type $adapterType and its primary constructor must be visible from $type",
          fallbackAdapterAnnotation
        )
        return
      }
      val hasMoshiParam =
        when (constructor?.parameters?.size) {
          null,
          0 -> {
            // Nothing to do
            false
          }
          1 -> {
            // Check it's a Moshi parameter
            val moshiParam = constructor.parameters[0]
            // TODO can this be simpler?
            if (!symbols.moshi.isAssignableFrom(moshiParam.type.resolve())) {
              logger.error(
                "Fallback adapter type's primary constructor can only have a Moshi parameter",
                fallbackAdapterAnnotation
              )
              return
            }
            true
          }
          else -> {
            logger.error(
              "Fallback adapter type's primary constructor can only have a Moshi parameter",
              fallbackAdapterAnnotation
            )
            return
          }
        }
      fallbackStrategy =
        FallbackStrategy.FallbackAdapter(
          className = adapterType.toClassName(),
          hasMoshiParam = hasMoshiParam
        )
    } else if (useDefaultNull) {
      fallbackStrategy = FallbackStrategy.Null
    }

    val objectAdapters = mutableListOf<CodeBlock>()
    val seenLabels = mutableMapOf<String, ClassName>()
    val originatingKSFiles = mutableSetOf<KSFile>()
    val nestedSealedClassNames = mutableSetOf<ClassName>()
    type.containingFile?.let(originatingKSFiles::add)
    val sealedSubtypes =
      type.getSealedSubclasses().flatMapTo(LinkedHashSet()) { subtype ->
        val className = subtype.toClassName()
        val isObject = subtype.classKind == OBJECT
        if (isObject && subtype.hasAnnotation(symbols.defaultObject)) {
          if (useDefaultNull) {
            // Print both for reference
            logger.error(
              """
                Cannot have both @DefaultNull and @DefaultObject. @DefaultObject type: $type
                Cannot have both @DefaultNull and @DefaultObject. @DefaultNull type: $subtype
              """
                .trimIndent(),
              subtype
            )
            return
          } else {
            return@flatMapTo sequenceOf(Subtype.ObjectType(className))
          }
        } else {
          walkTypeLabels(
            rootType = type,
            subtype = subtype,
            symbols = symbols,
            labelKey = labelKey,
            seenLabels = seenLabels,
            objectAdapters = objectAdapters,
            originatingKSFiles = originatingKSFiles,
            nestedSealedClassNames = nestedSealedClassNames,
            className = className
          )
        }
      }

    createType(
        targetType = type.toClassName(),
        isInternal = Modifier.INTERNAL in type.modifiers,
        labelKey = labelKey,
        fallbackStrategy = fallbackStrategy,
        generatedAnnotation = generatedAnnotation,
        nestedSealedClassNames = nestedSealedClassNames,
        subtypes = sealedSubtypes,
        objectAdapters = objectAdapters,
        generateProguardConfig = generateProguardConfig,
        errorLogger = { message -> logger.error(message, type) }
      ) {
        addAnnotation(COMMON_SUPPRESS)
        for (file in originatingKSFiles) {
          addOriginatingKSFile(file)
        }
      }
      ?.let { (spec, proguardConfig) ->
        spec.writeTo(codeGenerator, aggregating = true)
        proguardConfig?.writeTo(codeGenerator, type.containingFile)
      }
  }

  private fun walkTypeLabels(
    rootType: KSClassDeclaration,
    subtype: KSClassDeclaration,
    symbols: MoshiSealedSymbols,
    labelKey: String,
    seenLabels: MutableMap<String, ClassName>,
    objectAdapters: MutableList<CodeBlock>,
    originatingKSFiles: MutableSet<KSFile>,
    nestedSealedClassNames: MutableSet<ClassName>,
    className: ClassName = subtype.toClassName(),
  ): Sequence<Subtype> {
    subtype.containingFile?.let(originatingKSFiles::add)
    // If it's sealed, check if it's inheriting from our existing type or a separate/new branching
    // off point
    if (Modifier.SEALED in subtype.modifiers) {
      val nestedLabelKey =
        subtype.findAnnotationWithType(symbols.jsonClass)?.labelKey(checkGenerateAdapter = false)
      if (nestedLabelKey != null) {
        // Redundant case
        if (labelKey == nestedLabelKey) {
          error(
            "Sealed subtype $subtype is redundantly annotated with @JsonClass(generator = " +
              "\"sealed:$nestedLabelKey\")."
          )
        }
      }

      if (subtype.findAnnotationWithType(symbols.typeLabel) != null) {
        // It's a different type, allow it to be used as a label and branch off from here.
        val classType =
          addLabelKeyForType(
            rootType,
            subtype,
            symbols,
            seenLabels,
            objectAdapters,
            className,
            skipJsonClassCheck = true
          )
        return classType?.let { sequenceOf(it) } ?: emptySequence()
      } else {
        // Note this is an intermediate nested sealed type
        if (subtype.hasAnnotation(symbols.nestedSealed)) {
          nestedSealedClassNames += className
        }
        // Add the file as an originating element as it's indirectly participating in adapter
        // generation
        originatingKSFiles += subtype.containingFile!!
        // Recurse, inheriting the top type
        return subtype.getSealedSubclasses().flatMap {
          walkTypeLabels(
            rootType = rootType,
            subtype = it,
            symbols = symbols,
            labelKey = labelKey,
            seenLabels = seenLabels,
            objectAdapters = objectAdapters,
            originatingKSFiles = originatingKSFiles,
            nestedSealedClassNames = nestedSealedClassNames,
          )
        }
      }
    } else {
      val classType =
        addLabelKeyForType(
          rootType = rootType,
          subtype = subtype,
          symbols = symbols,
          seenLabels = seenLabels,
          objectAdapters = objectAdapters,
          className = className
        )
      return classType?.let { sequenceOf(it) } ?: emptySequence()
    }
  }

  private fun addLabelKeyForType(
    rootType: KSClassDeclaration,
    subtype: KSClassDeclaration,
    symbols: MoshiSealedSymbols,
    seenLabels: MutableMap<String, ClassName>,
    objectAdapters: MutableList<CodeBlock>,
    className: ClassName = subtype.toClassName(),
    skipJsonClassCheck: Boolean = false
  ): Subtype? {
    // Regular subtype, read its label
    val labelAnnotation =
      subtype.findAnnotationWithType(symbols.typeLabel)
        ?: run {
          logger.error("Missing @TypeLabel", subtype)
          return null
        }

    if (subtype.typeParameters.isNotEmpty()) {
      logger.error("Moshi-sealed subtypes cannot be generic.", subtype)
      return null
    }

    val labels = mutableListOf<String>()

    val mainLabel =
      labelAnnotation.arguments.find { it.name?.getShortName() == "label" }?.value as? String
        ?: run {
          logger.error("No label member for TypeLabel annotation!")
          return null
        }

    seenLabels.put(mainLabel, className)?.let { prev ->
      logger.error("Duplicate label '$mainLabel' defined for $className and $prev.", rootType)
      return null
    }

    labels += mainLabel

    // https://github.com/google/ksp/issues/134
    @Suppress("UNCHECKED_CAST")
    val alternates =
      labelAnnotation.arguments.find { it.name?.getShortName() == "alternateLabels" }?.value
        as? List<String> // arrays are lists in KSP https://github.com/google/ksp/issues/135
       ?: emptyList() // ksp ignores undefined args

    for (alternate in alternates) {
      seenLabels.put(alternate, className)?.let { prev ->
        logger.error(
          "Duplicate alternate label '$alternate' defined for $className and $prev.",
          rootType
        )
        return null
      }
    }

    if (!skipJsonClassCheck) {
      val labelKey = subtype.findAnnotationWithType(symbols.jsonClass)?.labelKey()
      if (labelKey != null) {
        logger.error(
          "Sealed subtype $subtype is annotated with @JsonClass(generator = \"sealed:$labelKey\") and @TypeLabel.",
          subtype
        )
        return null
      }
    }

    labels += alternates

    if (subtype.classKind == OBJECT) {
      objectAdapters.add(
        CodeBlock.of(
          ".%1M<%2T>(%3T(%2T))",
          MemberName("com.squareup.moshi", "addAdapter"),
          className,
          ObjectJsonAdapter::class.asClassName()
        )
      )
    }

    return Subtype.ClassType(className, labels)
  }
}

internal sealed interface FallbackStrategy {
  fun statement(moshiParam: CodeBlock): CodeBlock

  object Null : FallbackStrategy {
    override fun statement(moshiParam: CodeBlock) = CodeBlock.of(".withDefaultValue(null)")
  }

  class FallbackAdapter(val className: TypeName, val hasMoshiParam: Boolean) : FallbackStrategy {
    // TODO handle which moshi param comes in here
    override fun statement(moshiParam: CodeBlock): CodeBlock {
      val constructorParams =
        if (hasMoshiParam) {
          moshiParam
        } else {
          CodeBlock.of("")
        }
      return CodeBlock.of(
        ".withFallbackJsonAdapter(%T(%L)·as·%T<%T>)",
        className,
        constructorParams,
        JsonAdapter::class.asClassName(),
        ANY
      )
    }
  }

  class DefaultObject(val className: TypeName) : FallbackStrategy {
    override fun statement(moshiParam: CodeBlock) = CodeBlock.of(".withDefaultValue(%T)", className)
  }
}

/** Writes this to `filer`. */
internal fun ProguardConfig.writeTo(codeGenerator: CodeGenerator, originatingKSFile: KSFile?) {
  codeGenerator
    .createNewFile(
      Dependencies(false, sources = originatingKSFile?.let { arrayOf(it) } ?: emptyArray()),
      packageName = "",
      fileName = outputFile,
      extensionName = "",
    )
    .bufferedWriter()
    .use(::writeTo)
}
