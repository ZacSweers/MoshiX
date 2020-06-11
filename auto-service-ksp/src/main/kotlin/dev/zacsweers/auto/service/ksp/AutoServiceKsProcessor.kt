package dev.zacsweers.auto.service.ksp

import com.google.auto.service.AutoService
import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import org.jetbrains.kotlin.ksp.processing.CodeGenerator
import org.jetbrains.kotlin.ksp.processing.Resolver
import org.jetbrains.kotlin.ksp.processing.SymbolProcessor
import org.jetbrains.kotlin.ksp.symbol.KSClassDeclaration

@AutoService(SymbolProcessor::class)
class AutoServiceKsProcessor : SymbolProcessor {

  companion object {
    val AUTO_SERVICE_NAME = AutoService::class.qualifiedName!!
  }

  /**
   * Maps the class names of service provider interfaces to the
   * class names of the concrete classes which implement them.
   *
   * For example,
   * ```
   * "com.google.apphosting.LocalRpcService" -> "com.google.apphosting.datastore.LocalDatastoreService"
   * ```
   */
  private val providers: Multimap<String, String> = HashMultimap.create()

  private lateinit var codeGenerator: CodeGenerator

  override fun init(options: Map<String, String>, kotlinVersion: KotlinVersion,
      codeGenerator: CodeGenerator) {
    this.codeGenerator = codeGenerator
  }

  /**
   * - For each class annotated with [AutoService
   *   - Verify the [AutoService] interface value is correct
   *   - Categorize the class by its service interface
   * - For each [AutoService] interface
   *   - Create a file named `META-INF/services/<interface>`
   *   - For each [AutoService] annotated class for this interface
   *     - Create an entry in the file
   */
  override fun process(resolver: Resolver) {
    val autoServiceType = resolver.getClassDeclarationByName(
        resolver.getKSNameFromString(AUTO_SERVICE_NAME))
        ?.asType(emptyList())
        ?: error("@AutoService type not found on the classpath.")

    resolver.getSymbolsWithAnnotation(AUTO_SERVICE_NAME)
        .asSequence()
        .filterIsInstance<KSClassDeclaration>()
        .forEach {
          val annotation = it.annotations.find { it.annotationType.resolve() == autoServiceType }
              ?: error("@AutoService annotation not found")

          val providerInterfaces = annotation.arguments
              .find { it.name?.getShortName() == "value" }!!
              .value

          // Bug - this comes back as an empty list: https://github.com/android/kotlin/issues/5
          println(providerInterfaces)
        }
  }

  override fun finish() {

  }
}