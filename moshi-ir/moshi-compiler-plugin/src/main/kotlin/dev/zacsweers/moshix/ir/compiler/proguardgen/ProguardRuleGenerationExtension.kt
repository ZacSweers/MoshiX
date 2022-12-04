package dev.zacsweers.moshix.ir.compiler.proguardgen

import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.fqName
import com.squareup.anvil.compiler.internal.reference.argumentAt
import com.squareup.anvil.compiler.internal.reference.asClassName
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asClassName
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.codegen.api.InternalMoshiCodegenApi
import com.squareup.moshi.kotlin.codegen.api.ProguardConfig
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.com.intellij.openapi.extensions.ExtensionPoint
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.com.intellij.psi.PsiTreeChangeAdapter
import org.jetbrains.kotlin.com.intellij.psi.PsiTreeChangeListener
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.isSealed
import org.jetbrains.kotlin.descriptors.resolveClassByFqName
import org.jetbrains.kotlin.incremental.KotlinLookupLocation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension

private val MOSHI_REFLECTIVE_NAME = Moshi::class.asClassName().reflectionName()
private val TYPE_ARRAY_REFLECTIVE_NAME =
  "${java.lang.reflect.Type::class.asClassName().reflectionName()}[]"
private val NESTED_SEALED_FQ_NAME = FqName("dev.zacsweers.moshix.sealed.annotations.NestedSealed")
private val JSON_CLASS_FQ_NAME = JsonClass::class.fqName

internal class ProguardRuleGenerationExtension(
  private val messageCollector: MessageCollector,
  private val resourcesDir: File,
  private val enableSealed: Boolean,
  private val debug: Boolean
) : AnalysisHandlerExtension {

  private var initialized = false
  private lateinit var generator: ProguardRuleGenerator

  @OptIn(InternalMoshiCodegenApi::class)
  override fun doAnalysis(
    project: Project,
    module: ModuleDescriptor,
    projectContext: ProjectContext,
    files: Collection<KtFile>,
    bindingTrace: BindingTrace,
    componentProvider: ComponentProvider
  ): AnalysisResult? {
    val moshiModule = MoshiModuleDescriptor(module)

    resourcesDir.listFiles()?.forEach {
      check(it.deleteRecursively()) { "Could not clean file: $it" }
    }

    val psiManager = PsiManager.getInstance(project)
    if (!initialized) {
      // Dummy extension point; Required by dropPsiCaches().
      project.extensionArea.registerExtensionPoint(
        PsiTreeChangeListener.EP.name,
        PsiTreeChangeAdapter::class.java.canonicalName,
        ExtensionPoint.Kind.INTERFACE
      )
      generator = ProguardRuleGenerator(resourcesDir)
      initialized = true
    } else {
      psiManager.dropPsiCaches()
    }

    files.classAndInnerClassReferences(moshiModule).forEach { psiClass ->
      val jsonClassAnnotation =
        psiClass.annotations.find { it.fqName == JSON_CLASS_FQ_NAME } ?: return@forEach

      if (!jsonClassAnnotation.argumentAt("generateAdapter", 0)!!.value<Boolean>()) {
        return@forEach
      }
      val generatorKey = jsonClassAnnotation.argumentAt("generator", 1)?.value() ?: ""
      val isMoshiSealed = (enableSealed && generatorKey.startsWith("sealed:"))
      if (generatorKey.isEmpty() || isMoshiSealed) {
        val targetType = psiClass.asClassName()
        val hasGenerics = !psiClass.typeParameters.isNullOrEmpty()
        val adapterName = "${targetType.simpleNames.joinToString(separator = "_")}JsonAdapter"
        val adapterConstructorParams =
          when (hasGenerics) {
            false -> listOf(MOSHI_REFLECTIVE_NAME)
            true -> listOf(MOSHI_REFLECTIVE_NAME, TYPE_ARRAY_REFLECTIVE_NAME)
          }

        val nestedSealedClassNames: Set<ClassName>
        if (isMoshiSealed && psiClass.clazz.hasModifier(KtTokens.SEALED_KEYWORD)) {
          nestedSealedClassNames = mutableSetOf()
          val descriptor =
            moshiModule.resolveClassByFqName(psiClass.fqName, KotlinLookupLocation(psiClass.clazz))
              ?: throw MoshiCompilationException(
                "Could not resolve class descriptor for ${psiClass.fqName}",
                null,
                psiClass.clazz
              )
          // Skip initial annotation check because this is top level
          descriptor.walkSealedSubtypes(nestedSealedClassNames, skipAnnotationCheck = true)
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
            targetConstructorParams = emptyList()
          )
        val fileName = "${targetType.canonicalName}.pro"
        if (debug) {
          messageCollector.report(
            CompilerMessageSeverity.WARNING,
            "MOSHI: Writing rules for $fileName: $config"
          )
        }
        generator
          .createNewFile(config.outputFilePathWithoutExtension(fileName))
          .bufferedWriter()
          .use { writer ->
            config.writeTo(writer)
            if (nestedSealedClassNames.size > 0) {
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

    generator.closeFiles()

    return null
  }
}

private class ProguardRuleGenerator(resourcesDir: File) {
  private val typeRoot = resourcesDir.path
  private val fileMap = mutableMapOf<String, File>()
  private val fileOutputStreamMap = mutableMapOf<String, FileOutputStream>()
  private val separator = File.separator

  // This function will also clear `fileOutputStreamMap` which will change the result of
  // `generatedFile`
  fun closeFiles() {
    fileOutputStreamMap.values.forEach(FileOutputStream::close)
    fileOutputStreamMap.clear()
  }

  fun createNewFile(fileName: String): OutputStream {
    val path = "$typeRoot$separator$fileName".replace("/", separator)
    if (fileOutputStreamMap[path] == null) {
      if (fileMap[path] == null) {
        val file = File(path)
        val parentFile = file.parentFile
        if (!parentFile.exists() && !parentFile.mkdirs()) {
          throw IllegalStateException("failed to make parent directories.")
        }
        file.writeText("")
        fileMap[path] = file
      }
      fileOutputStreamMap[path] = fileMap.getValue(path).outputStream()
    }
    return fileOutputStreamMap.getValue(path)
  }
}

private fun ClassDescriptor.walkSealedSubtypes(
  elements: MutableSet<ClassName>,
  skipAnnotationCheck: Boolean
) {
  if (isSealed()) {
    if (!skipAnnotationCheck) {
      if (annotations.findAnnotation(NESTED_SEALED_FQ_NAME) != null) {
        elements += asClassName()
      } else {
        return
      }
    }
    for (nested in sealedSubclasses) {
      nested.walkSealedSubtypes(elements, skipAnnotationCheck = false)
    }
  }
}
