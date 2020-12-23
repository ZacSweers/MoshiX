package dev.zacsweers.forge.factory

import com.google.auto.service.AutoService
import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.AnnotationUseSiteTarget.GET
import com.google.devtools.ksp.symbol.ClassKind.CLASS
import com.google.devtools.ksp.symbol.ClassKind.OBJECT
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.jvm.jvmStatic
import dagger.Provides
import dagger.internal.Factory
import dagger.internal.Preconditions
import dev.zacsweers.forge.factory.KSCallableDeclaration.Companion
import java.util.Locale.US

@AutoService(SymbolProcessor::class)
public class ForgeProcessor : SymbolProcessor {

  private lateinit var codeGenerator: CodeGenerator

  override fun init(
    options: Map<String, String>,
    kotlinVersion: KotlinVersion,
    codeGenerator: CodeGenerator,
    logger: KSPLogger
  ) {
    this.codeGenerator = codeGenerator
  }

  override fun process(resolver: Resolver) {
    resolver.getSymbolsWithAnnotation("dagger.Module")
      .asSequence()
      .map { it as KSClassDeclaration }
      .forEach { moduleClass ->
        val companionObject = moduleClass.companionObject()
        val providesFunctions = companionObject?.getAllFunctions()
          .orEmpty()
          .asSequence()
          .plus(moduleClass.getAllFunctions().asSequence())
          .filter { it.hasAnnotation(resolver.getClassDeclarationByName<Provides>()!!.asType()) }
          .mapNotNullTo(mutableListOf(), Companion::create)
          .also { providesFunctions ->
            // Check for duplicate function names. empty TODO
            val duplicateFunctions = providesFunctions
              .groupBy { it.qualifiedName!! }
              .filterValues { it.size > 1 }

            if (duplicateFunctions.isNotEmpty()) {
              error("TODO duplicates")
//              throw CompilationException(
//                element = clazz,
//                message = "Cannot have more than one binding method with the same name in " +
//                  "a single module: ${duplicateFunctions.keys.joinToString()}"
//              )
            }
          }

        val providesProperties = moduleClass
          .getAllProperties()
          .asSequence()
          .filter { property ->
            // Must be '@get:Provides'.
            val annotation = property.findAnnotationWithType(
              resolver.getClassDeclarationByName<Provides>()!!.asType()) ?: return@filter false
            annotation.useSiteTarget == GET
          }
          .mapNotNullTo(mutableListOf(), Companion::create)
          .toList()

        val callableDeclarations = providesFunctions + providesProperties

        callableDeclarations
          .forEach { callable ->
            generateFactoryClass(resolver, moduleClass, callable)
          }
      }
  }

//  private fun generateFactoryClass(
//    moduleClass: KSClassDeclaration,
//    function: KSPropertyDeclaration
//  ) {
//
//  }

  private fun generateFactoryClass(
    resolver: Resolver,
    moduleClassDeclaration: KSClassDeclaration,
    declaration: KSCallableDeclaration
  ) {
    val isCompanionObject = moduleClassDeclaration.isCompanionObject
    val isObject = moduleClassDeclaration.classKind == OBJECT
    val isProperty = declaration is KSPropertyDeclaration
    val packageName = moduleClassDeclaration.packageName.asString()
    val className = buildString {
      append(moduleClassDeclaration.generateClassName())
      append('_')
      if (isCompanionObject) {
        append("Companion_")
      }
      if (isProperty) {
        append("Get")
      }
      append(declaration.qualifiedName!!.getShortName().capitalize(US))
      append("Factory")
    }

    val callableName = declaration.simpleName.asString()

    val classTypeParamsResolver = moduleClassDeclaration.typeParameters.toTypeParameterResolver(
      sourceType = moduleClassDeclaration.qualifiedName!!.asString())
    val parameters = declaration.parameters.mapToParameter(resolver, classTypeParamsResolver)

    val returnType = declaration.type.toTypeName(classTypeParamsResolver)
      .withJvmSuppressWildcardsIfNeeded(resolver, declaration.type)

    val returnTypeIsNullable = returnType.isNullable

    val factoryClass = ClassName(packageName, className)
    val moduleClass = moduleClassDeclaration.toClassName()

    val byteCodeFunctionName = if (isProperty) {
      "get" + callableName.capitalize(US)
    } else {
      callableName
    }

    FileSpec.builder(packageName, className)
      .apply {
        val canGenerateAnObject = isObject && parameters.isEmpty()
        val classBuilder = if (canGenerateAnObject) {
          TypeSpec.objectBuilder(factoryClass)
        } else {
          TypeSpec.classBuilder(factoryClass)
        }

        classBuilder
          .addGeneratedAnnotation()
          .addSuperinterface(Factory::class.asClassName().parameterizedBy(returnType))
          .apply {
            if (!canGenerateAnObject) {
              primaryConstructor(
                FunSpec.constructorBuilder()
                  .apply {
                    if (!isObject) {
                      addParameter("module", moduleClass)
                    }
                    parameters.forEach { parameter ->
                      addParameter(parameter.name, parameter.providerTypeName)
                    }
                  }
                  .build()
              )

              if (!isObject) {
                addProperty(
                  PropertySpec.builder("module", moduleClass)
                    .initializer("module")
                    .addModifiers(PRIVATE)
                    .build()
                )
              }

              parameters.forEach { parameter ->
                addProperty(
                  PropertySpec.builder(parameter.name, parameter.providerTypeName)
                    .initializer(parameter.name)
                    .addModifiers(PRIVATE)
                    .build()
                )
              }
            }
          }
          .addFunction(
            FunSpec.builder("get")
              .addModifiers(OVERRIDE)
              .returns(returnType)
              .apply {
                val argumentList = parameters.asArgumentList(
                  asProvider = true,
                  includeModule = !isObject
                )
                addStatement("return $byteCodeFunctionName($argumentList)")
              }
              .build()
          )
          .apply {
            val builder = if (canGenerateAnObject) this else TypeSpec.companionObjectBuilder()
            builder
              .addFunction(
                FunSpec.builder("create")
                  .jvmStatic()
                  .apply {
                    if (canGenerateAnObject) {
                      addStatement("return this")
                    } else {
                      if (!isObject) {
                        addParameter("module", moduleClass)
                      }
                      parameters.forEach { parameter ->
                        addParameter(parameter.name, parameter.providerTypeName)
                      }

                      val argumentList = parameters.asArgumentList(
                        asProvider = false,
                        includeModule = !isObject
                      )

                      addStatement("return %T($argumentList)", factoryClass)
                    }
                  }
                  .returns(factoryClass)
                  .build()
              )
              .addFunction(
                FunSpec.builder(byteCodeFunctionName)
                  .jvmStatic()
                  .apply {
                    if (!isObject) {
                      addParameter("module", moduleClass)
                    }

                    parameters.forEach { parameter ->
                      addParameter(
                        name = parameter.name,
                        type = parameter.originalTypeName
                      )
                    }

                    val argumentsWithoutModule = if (isProperty) {
                      ""
                    } else {
                      "(${parameters.joinToString { it.name }})"
                    }

                    when {
                      isObject && returnTypeIsNullable ->
                        addStatement(
                          "return %T.$callableName$argumentsWithoutModule",
                          moduleClass
                        )
                      isObject && !returnTypeIsNullable ->
                        addStatement(
                          "return %T.checkNotNull(%T.$callableName" +
                            "$argumentsWithoutModule, %S)",
                          Preconditions::class,
                          moduleClass,
                          "Cannot return null from a non-@Nullable @Provides method"
                        )
                      !isObject && returnTypeIsNullable ->
                        addStatement(
                          "return module.$callableName$argumentsWithoutModule"
                        )
                      !isObject && !returnTypeIsNullable ->
                        addStatement(
                          "return %T.checkNotNull(module.$callableName" +
                            "$argumentsWithoutModule, %S)",
                          Preconditions::class,
                          "Cannot return null from a non-@Nullable @Provides method"
                        )
                    }
                  }
                  .returns(returnType)
                  .build()
              )
              .build()
              .let {
                if (!canGenerateAnObject) {
                  addType(it)
                }
              }
          }
          .build()
          .let { addType(it) }
      }
      .addGeneratedByComment()
      .build()
      .writeTo(codeGenerator)
  }

  private fun KSClassDeclaration.companionObject(): KSClassDeclaration? {
    return declarations.find { it is KSClassDeclaration && it.isCompanionObject } as? KSClassDeclaration
  }

  override fun finish() {

  }
}

internal fun KSClassDeclaration.asType() = asType(emptyList())

internal fun KSAnnotated.hasAnnotation(target: KSType): Boolean {
  return findAnnotationWithType(target) != null
}

internal fun KSAnnotated.findAnnotationWithType(target: KSType): KSAnnotation? {
  return annotations.find { it.annotationType.resolve() == target }
}

internal fun KSClassDeclaration.generateClassName(
  separator: String = "_"
): String =
  getAllSuperTypes()
    .map { it.declaration }
    .filterIsInstance<KSClassDeclaration>()
    .filter { it.classKind == CLASS }
    .toList()
    .let { it + this@generateClassName }
    .reversed()
    .joinToString(separator = separator) {
      it.qualifiedName!!
        .getShortName()
    }