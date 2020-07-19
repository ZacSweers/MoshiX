package dev.zacsweers.moshisealed.codegen

import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.moshi.JsonClass
import dev.zacsweers.moshisealed.annotations.DefaultNull
import dev.zacsweers.moshisealed.annotations.DefaultObject
import dev.zacsweers.moshisealed.annotations.TypeLabel
import org.jetbrains.kotlin.ksp.processing.CodeGenerator
import org.jetbrains.kotlin.ksp.processing.Resolver
import org.jetbrains.kotlin.ksp.processing.SymbolProcessor
import org.jetbrains.kotlin.ksp.symbol.ClassKind.OBJECT
import org.jetbrains.kotlin.ksp.symbol.KSClassDeclaration
import org.jetbrains.kotlin.ksp.symbol.KSDeclarationContainer
import org.jetbrains.kotlin.ksp.symbol.Modifier

@AutoService(SymbolProcessor::class)
class MoshiSealedSymbolProcessor : SymbolProcessor {

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

    val JSON_CLASS_NAME = JsonClass::class.qualifiedName!!
  }

  private lateinit var codeGenerator: CodeGenerator
  private var generatedOption: String? = null

  override fun init(options: Map<String, String>, kotlinVersion: KotlinVersion,
      codeGenerator: CodeGenerator) {
    this.codeGenerator = codeGenerator

    generatedOption = options[OPTION_GENERATED]?.also {
      require(it in POSSIBLE_GENERATED_NAMES) {
        "Invalid option value for $OPTION_GENERATED. Found $it, allowable values are $POSSIBLE_GENERATED_NAMES."
      }
    }
  }

  override fun process(resolver: Resolver) {
    val generatedAnnotation = generatedOption?.let {
      val annotationType = resolver.getClassDeclarationByName(resolver.getKSNameFromString(it))
          ?: error("Generated annotation type doesn't exist: $it")
      AnnotationSpec.builder(annotationType.toClassName())
          .addMember("value = [%S]", MoshiSealedSymbolProcessor::class.java.canonicalName)
          .addMember("comments = %S", "https://github.com/ZacSweers/moshi-sealed")
          .build()
    }

    val jsonClassType = resolver.getClassDeclarationByName(
        resolver.getKSNameFromString(JSON_CLASS_NAME))
        ?.asType()
        ?: error("JsonClass type not found on the classpath.")
    resolver.getSymbolsWithAnnotation(JSON_CLASS_NAME)
        .asSequence()
        .forEach { type ->
          check(type is KSClassDeclaration) {
            "@JsonClass is only applicable to classes!"
          }

          val generator = type.findAnnotationWithType(jsonClassType)
              ?.getMember<String>("generator")
              ?: return@forEach

          if (!generator.startsWith("sealed:")) {
            return@forEach
          }

          check(Modifier.SEALED in type.modifiers) {
            "Must be a sealed class!"
          }

          val typeLabel = generator.removePrefix("sealed:")
          createType(resolver, type, typeLabel, generatedAnnotation)
        }
  }

  private fun createType(
      resolver: Resolver,
      type: KSClassDeclaration,
      typeLabel: String,
      generatedAnnotation: AnnotationSpec?
  ) {
    val defaultNullAnnotation = resolver.getClassDeclarationByName<DefaultNull>().asType()
    val defaultObjectAnnotation = resolver.getClassDeclarationByName<DefaultObject>().asType()
    val typeLabelAnnotation = resolver.getClassDeclarationByName<TypeLabel>().asType()
    val useDefaultNull = type.hasAnnotation(defaultNullAnnotation)
    val sealedSubtypes = type.sealedSubtypes()
        .mapNotNullTo(LinkedHashSet()) { subtype ->
          val className = subtype.toClassName()
          if (subtype.classKind == OBJECT) {
            if (subtype.hasAnnotation(defaultObjectAnnotation)) {
              if (useDefaultNull) {
                // Print both for reference
                error("""
                  Cannot have both @DefaultNull and @DefaultObject. @DefaultObject type: $type
                  Cannot have both @DefaultNull and @DefaultObject. @DefaultNull type: $subtype
                """.trimIndent())
              } else {
                return@mapNotNullTo Subtype(
                    className = className,
                    isDefaultObject = true,
                    typeLabelType = null,
                    originatingElement = null
                )
              }
            } else if (!useDefaultNull) {
              error("Unhandled object type, cannot serialize this. $type")
            }
            return@mapNotNullTo null
          } else {
            val labelAnnotation = subtype.findAnnotationWithType(typeLabelAnnotation)
                ?: error("Missing @TypeLabel: $subtype")
            val labelValue = labelAnnotation.arguments
                .find { it.name?.getShortName() == "value" }
                ?.value as? String
                ?: error("No value found for label annotation!")
            Subtype(className, false, labelValue, null)
          }
        }

    createType(
        targetType = type.toClassName(),
        isInternal = Modifier.INTERNAL in type.modifiers,
        typeLabel = typeLabel,
        useDefaultNull = useDefaultNull,
        originatingElement = null,
        generatedAnnotation = generatedAnnotation,
        subtypes = sealedSubtypes
    )
        .writeTo(codeGenerator)
  }

  private fun KSClassDeclaration.sealedSubtypes(): Set<KSClassDeclaration> {
    // All sealed subtypes are guaranteed to to be in this file... somewhere
    val targetSuperClass = asType()
    return containingFile?.allTypes()
        ?.filter { it.superTypes.firstOrNull()?.resolve() == targetSuperClass }
        ?.toSet()
        .orEmpty()
  }

  private fun KSDeclarationContainer.allTypes(): Sequence<KSClassDeclaration> {
    val sequence = declarations.asSequence().filterIsInstance<KSClassDeclaration>()
        .flatMap { it.allTypes() }
    return if (this is KSClassDeclaration) {
      sequence + this
    } else {
      sequence
    }
  }

  override fun finish() {

  }
}