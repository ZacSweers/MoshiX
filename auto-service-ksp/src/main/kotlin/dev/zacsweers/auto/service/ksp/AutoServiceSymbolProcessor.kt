package dev.zacsweers.auto.service.ksp

import com.google.auto.service.AutoService
import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import com.google.common.collect.Sets
import com.squareup.kotlinpoet.ClassName
import org.jetbrains.kotlin.ksp.closestClassDeclaration
import org.jetbrains.kotlin.ksp.getAllSuperTypes
import org.jetbrains.kotlin.ksp.processing.CodeGenerator
import org.jetbrains.kotlin.ksp.processing.Resolver
import org.jetbrains.kotlin.ksp.processing.SymbolProcessor
import org.jetbrains.kotlin.ksp.symbol.KSClassDeclaration
import org.jetbrains.kotlin.ksp.symbol.KSType
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.SortedSet

@AutoService(SymbolProcessor::class)
class AutoServiceSymbolProcessor : SymbolProcessor {

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
  private var verify = false
  private var verbose = false

  override fun init(
      options: Map<String, String>,
      kotlinVersion: KotlinVersion,
      codeGenerator: CodeGenerator
  ) {
    this.codeGenerator = codeGenerator
    verify = options["autoserviceKsp.verify"]?.toBoolean() == true
    verbose = options["autoserviceKsp.verbose"]?.toBoolean() == true
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
        .forEach { providerImplementer ->
          val annotation = providerImplementer.annotations.find { it.annotationType.resolve() == autoServiceType }
              ?: error("@AutoService annotation not found")

          @Suppress("UNCHECKED_CAST")
          val providerInterfaces = annotation.arguments
              .find { it.name?.getShortName() == "value" }!!
              .value as? List<KSType>
              ?: error("No 'value' member value found!")

          if (providerInterfaces.isEmpty()) {
            error("No service interfaces provided for element! $providerImplementer")
          }

          for (providerType in providerInterfaces) {
            val providerDecl = providerType.declaration.closestClassDeclaration()!!
            if (checkImplementer(providerImplementer, providerType)) {
              providers.put(providerDecl.toBinaryName(), providerImplementer.toBinaryName())
            } else {
              val message = "ServiceProviders must implement their service provider interface. " +
                  providerImplementer.qualifiedName +
                  " does not implement " +
                  providerDecl.qualifiedName
              error(message)
            }
          }
        }
    generateConfigFiles()
  }

  private fun checkImplementer(
      providerImplementer: KSClassDeclaration,
      providerType: KSType
  ): Boolean {
    if (!verify) {
      return true
    }
    return providerImplementer.getAllSuperTypes().any { it.isAssignableFrom(providerType) }
  }

  private fun generateConfigFiles() {
    for (providerInterface in providers.keySet()) {
      val resourceFile = "resources/META-INF/services/$providerInterface"
      log("Working on resource file: $resourceFile")
      try {
        // TODO read all existing files?
        val allServices: SortedSet<String> = Sets.newTreeSet()
//        try {
//          // would like to be able to print the full path
//          // before we attempt to get the resource in case the behavior
//          // of filer.getResource does change to match the spec, but there's
//          // no good way to resolve CLASS_OUTPUT without first getting a resource.
//          val existingFile: FileObject = filer.getResource(CLASS_OUTPUT, "",
//              resourceFile)
//          log("Looking for existing resource file at " + existingFile.toUri())
//          val oldServices: Set<String> = ServicesFiles.readServiceFile(
//              existingFile.openInputStream())
//          log("Existing service entries: $oldServices")
//          allServices.addAll(oldServices)
//        } catch (e: IOException) {
//          // According to the javadoc, Filer.getResource throws an exception
//          // if the file doesn't already exist.  In practice this doesn't
//          // appear to be the case.  Filer.getResource will happily return a
//          // FileObject that refers to a non-existent file but will throw
//          // IOException if you try to open an input stream for it.
//          log("Resource file did not already exist.")
//        }
        val newServices: Set<String> = HashSet(providers[providerInterface])
//        if (allServices.containsAll(newServices)) {
//          log("No new service entries being added.")
//          return
//        }
        allServices.addAll(newServices)
        log("New service file contents: $allServices")
        codeGenerator.createNewFile("", resourceFile, "").bufferedWriter().use { writer ->
          for (service in allServices) {
            writer.write(service)
            writer.newLine()
          }
        }
        log("Wrote to: $resourceFile")
      } catch (e: IOException) {
        error("Unable to create $resourceFile, $e")
      }
    }
  }

  private fun FileOutputStream.tryGetFile(): File? {
    return try {
      FileOutputStream::class.java.getDeclaredField("path")
          .apply {
            isAccessible = true
          }
          .get(this)
          ?.let { File(it as String) }
    } catch (ignored: Exception) {
      null
    }
  }

  private fun log(message: String) {
    if (verbose) {
      // TODO needs a logging API
      // https://github.com/android/kotlin/issues/1
      println(message)
    }
  }

  /**
   * Returns the binary name of a reference type. For example,
   * {@code com.google.Foo$Bar}, instead of {@code com.google.Foo.Bar}.
   */
  private fun KSClassDeclaration.toBinaryName(): String {
    return toClassName().reflectionName()
  }

  private fun KSClassDeclaration.toClassName(): ClassName {
    // This doesn't work if your package name has capitals.
    // https://github.com/android/kotlin/issues/23
    val qualifierSplit = qualifiedName!!.getQualifier().split(".")
    val simple = simpleName.asString()
    val nameStart = qualifierSplit.indexOfFirst { it[0].isUpperCase() }
    val packageName = qualifierSplit.subList(0,
        if (nameStart != -1) nameStart else qualifierSplit.size)
        .joinToString(".")
    val intermediaryNames = if (nameStart == -1) {
      emptyList()
    } else {
      qualifierSplit.subList(nameStart, qualifierSplit.size)
    }
    val allNames = intermediaryNames + simple
    return ClassName(packageName, allNames)
  }

  override fun finish() {

  }
}