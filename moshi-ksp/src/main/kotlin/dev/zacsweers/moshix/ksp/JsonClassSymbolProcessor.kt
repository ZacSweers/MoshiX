package dev.zacsweers.moshix.ksp

import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.moshi.JsonClass
import dev.zacsweers.moshix.ksp.shade.api.AdapterGenerator
import dev.zacsweers.moshix.ksp.shade.api.ProguardConfig
import dev.zacsweers.moshix.ksp.shade.api.PropertyGenerator
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSClassDeclaration
import java.io.OutputStreamWriter
import java.lang.RuntimeException
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
    logger: KSPLogger
  ) {
    this.codeGenerator = codeGenerator
    this.logger = logger

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
        .addMember("value = [%S]", JsonClassSymbolProcessor::class.java.canonicalName)
        .addMember("comments = %S", "https://github.com/square/moshi")
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
          "@JsonClass can't be applied to $type: must be a Kotlin class"
        }

        val jsonClassAnnotation = type.findAnnotationWithType(jsonClassType) ?: return@forEach

        val generator = jsonClassAnnotation.getMember<String>("generator")

        if (generator.isNotEmpty()) return@forEach

        if (!jsonClassAnnotation.getMember<Boolean>("generateAdapter")) return@forEach

        val adapterGenerator = adapterGenerator(logger, resolver, type) ?: return@forEach
        val preparedAdapter = try {
          adapterGenerator
            .prepare { spec ->
              spec.toBuilder()
                .apply {
                  generatedAnnotation?.let(::addAnnotation)
                }
                .build()
            }
        } catch (e: Exception) {
          throw RuntimeException("Error preparing ${type.simpleName.asString()}", e)
        }

        preparedAdapter.spec.writeTo(codeGenerator)
        preparedAdapter.proguardConfig?.writeTo(codeGenerator)
      }
  }

  override fun finish() {

  }

  private fun ProguardConfig.writeTo(codeGenerator: CodeGenerator) {
    // TODO outputFile needs to be public
    val name = "META-INF/proguard/moshi-${targetClass.canonicalName}"
    val file = codeGenerator.createNewFile("", name, "pro")
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
        error("No property for required constructor parameter $name")
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
}