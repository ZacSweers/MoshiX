package dev.zacsweers.moshisealed.codegen

import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.moshi.JsonClass
import org.jetbrains.kotlin.ksp.processing.CodeGenerator
import org.jetbrains.kotlin.ksp.processing.Resolver
import org.jetbrains.kotlin.ksp.processing.SymbolProcessor
import org.jetbrains.kotlin.ksp.symbol.KSClassDeclaration

@AutoService(SymbolProcessor::class)
class MoshiSealedSp : SymbolProcessor {

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

  lateinit var codeGenerator: CodeGenerator
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
          .addMember("value = [%S]", MoshiSealedSp::class.java.canonicalName)
          .addMember("comments = %S", "https://github.com/ZacSweers/moshi-sealed")
          .build()
    }

    val jsonClassType = resolver.getClassDeclarationByName(
        resolver.getKSNameFromString(JSON_CLASS_NAME))
        ?.asType(emptyList())
        ?: error("JsonClass type not found on the classpath.")
    resolver.getSymbolsWithAnnotation(JSON_CLASS_NAME)
        .asSequence()
        .forEach { type ->
          check(type is KSClassDeclaration) {
            "@JsonClass is only applicable to classes!"
          }

          val generator = type.annotations.find { it.annotationType.resolve() == jsonClassType }
              ?.arguments
              ?.find { it.name?.getShortName() == "generator" }
              ?.value as? String
              ?: return@forEach

          if (!generator.startsWith("sealed:")) {
            return@forEach
          }

          val typeLabel = generator.removePrefix("sealed:")
        }
  }

  override fun finish() {

  }
}