package dev.zacsweers.moshix.ir.compiler.proguardgen

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
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension

private val MOSHI_REFLECTIVE_NAME = Moshi::class.asClassName().reflectionName()
private val TYPE_ARRAY_REFLECTIVE_NAME =
    "${java.lang.reflect.Type::class.asClassName().reflectionName()}[]"

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
          ExtensionPoint.Kind.INTERFACE)
      generator = ProguardRuleGenerator(resourcesDir)
      initialized = true
    } else {
      psiManager.dropPsiCaches()
    }

    files.flatMap(KtFile::classesAndInnerClasses).forEach { clazz ->
      val jsonClassAnnotation =
          clazz.findAnnotation(JsonClass::class.fqName, moshiModule) ?: return@forEach
      if (jsonClassAnnotation.findAnnotationArgument<Boolean>("generateAdapter", 0) == false) {
        return@forEach
      }
      val generatorKey = jsonClassAnnotation.findAnnotationArgument<String>("generator", 1) ?: ""
      if (generatorKey.isEmpty() || (enableSealed && generatorKey.startsWith("sealed:"))) {
        val targetType = clazz.asClassName()
        val hasGenerics = !clazz.typeParameterList?.parameters.isNullOrEmpty()
        val adapterName = "${targetType.simpleNames.joinToString(separator = "_")}JsonAdapter"
        val adapterConstructorParams =
            when (hasGenerics) {
              false -> listOf(MOSHI_REFLECTIVE_NAME)
              true -> listOf(MOSHI_REFLECTIVE_NAME, TYPE_ARRAY_REFLECTIVE_NAME)
            }
        val config =
            ProguardConfig(
                targetClass = targetType,
                adapterName = adapterName,
                adapterConstructorParams = adapterConstructorParams,
                // Not actually true but in our case we don't need the generated rules for htis
                targetConstructorHasDefaults = false,
                targetConstructorParams = emptyList())
        val fileName = "${targetType.canonicalName}.pro"
        if (debug) {
          messageCollector.report(
              CompilerMessageSeverity.WARNING, "MOSHI: Writing rules for $fileName: $config")
        }
        generator
            .createNewFile(config.outputFilePathWithoutExtension(fileName))
            .bufferedWriter()
            .use(config::writeTo)
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

private fun KtFile.classesAndInnerClasses(): List<KtClassOrObject> {
  val children = findChildrenByClass(KtClassOrObject::class.java)

  return generateSequence(children.toList()) { list ->
        list.flatMap { it.declarations.filterIsInstance<KtClassOrObject>() }.ifEmpty { null }
      }
      .flatten()
      .toList()
}
