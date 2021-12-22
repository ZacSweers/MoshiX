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
package dev.zacsweers.moshix.ir.compiler

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import com.tschuchort.compiletesting.PluginOption
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.SourceFile.Companion.kotlin
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.JvmTarget
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MoshiIrVisitorTest {

  @Rule @JvmField var temporaryFolder: TemporaryFolder = TemporaryFolder()

  @Test
  fun valueClassesCannotHaveDefaultValues() {
    val result =
        compile(
            kotlin(
                "source.kt",
                """
          package test

          import com.squareup.moshi.Json
          import com.squareup.moshi.JsonClass
          import com.squareup.moshi.JsonQualifier

          @JvmInline
          @JsonClass(generateAdapter = true)
          value class ValueClass(val i: Int = 0)
          """))
    assertThat(result.exitCode).isEqualTo(COMPILATION_ERROR)
    assertThat(result.messages)
        .contains("value classes with default values are not currently supported in Moshi code gen")
  }

  @Test
  fun privateConstructor() {
    val result =
        compile(
            kotlin(
                "source.kt",
                """
          package test
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          class PrivateConstructor private constructor(var a: Int, var b: Int) {
            fun a() = a
            fun b() = b
            companion object {
              fun newInstance(a: Int, b: Int) = PrivateConstructor(a, b)
            }
          }
          """))
    assertThat(result.exitCode).isEqualTo(COMPILATION_ERROR)
    assertThat(result.messages).contains("constructor is not internal or public")
  }

  @Test
  fun privateConstructorParameter() {
    val result =
        compile(
            kotlin(
                "source.kt",
                """
          package test
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          class PrivateConstructorParameter(private var a: Int)
          """))
    assertThat(result.exitCode).isEqualTo(COMPILATION_ERROR)
    assertThat(result.messages).contains("property a is not visible")
  }

  @Test
  fun privateProperties() {
    val result =
        compile(
            kotlin(
                "source.kt",
                """
          package test
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          class PrivateProperties {
            private var a: Int = -1
            private var b: Int = -1
          }
          """))
    assertThat(result.exitCode).isEqualTo(COMPILATION_ERROR)
    assertThat(result.messages).contains("property a is not visible")
  }

  @Test
  fun interfacesNotSupported() {
    val result =
        compile(
            kotlin(
                "source.kt",
                """
          package test
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          interface Interface
          """))
    assertThat(result.exitCode).isEqualTo(COMPILATION_ERROR)
    assertThat(result.messages)
        .contains("@JsonClass can't be applied to test.Interface: must be a Kotlin class")
  }

  @Test
  fun interfacesDoNotErrorWhenGeneratorNotSet() {
    val result =
        compile(
            kotlin(
                "source.kt",
                """
          package test
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true, generator="customGenerator")
          interface Interface
          """))
    assertThat(result.exitCode).isEqualTo(OK)
  }

  @Test
  fun abstractClassesNotSupported() {
    val result =
        compile(
            kotlin(
                "source.kt",
                """
          package test
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          abstract class AbstractClass(val a: Int)
          """))
    assertThat(result.exitCode).isEqualTo(COMPILATION_ERROR)
    assertThat(result.messages)
        .contains("@JsonClass can't be applied to test.AbstractClass: must not be abstract")
  }

  @Test
  fun sealedClassesNotSupported() {
    val result =
        compile(
            kotlin(
                "source.kt",
                """
          package test
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          sealed class SealedClass(val a: Int)
          """))
    assertThat(result.exitCode).isEqualTo(COMPILATION_ERROR)
    assertThat(result.messages)
        .contains("@JsonClass can't be applied to test.SealedClass: must not be sealed")
  }

  @Test
  fun innerClassesNotSupported() {
    val result =
        compile(
            kotlin(
                "source.kt",
                """
          package test
          import com.squareup.moshi.JsonClass

          class Outer {
            @JsonClass(generateAdapter = true)
            inner class InnerClass(val a: Int)
          }
          """))
    assertThat(result.exitCode).isEqualTo(COMPILATION_ERROR)
    assertThat(result.messages)
        .contains(
            "@JsonClass can't be applied to test.Outer.InnerClass: must not be an inner class")
  }

  @Test
  fun enumClassesNotSupported() {
    val result =
        compile(
            kotlin(
                "source.kt",
                """
          package test
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          enum class KotlinEnum {
            A, B
          }
          """))
    assertThat(result.exitCode).isEqualTo(COMPILATION_ERROR)
    assertThat(result.messages)
        .contains(
            "@JsonClass with 'generateAdapter = \"true\"' can't be applied to test.KotlinEnum: code gen for enums is not supported or necessary")
  }

  // Annotation processors don't get called for local classes, so we don't have the opportunity to
  @Ignore
  @Test
  fun localClassesNotSupported() {
    val result =
        compile(
            kotlin(
                "source.kt",
                """
          package test
          import com.squareup.moshi.JsonClass

          fun outer() {
            @JsonClass(generateAdapter = true)
            class LocalClass(val a: Int)
          }
          """))
    assertThat(result.exitCode).isEqualTo(COMPILATION_ERROR)
    assertThat(result.messages)
        .contains("@JsonClass can't be applied to LocalClass: must not be local")
  }

  @Test
  fun privateClassesNotSupported() {
    val result =
        compile(
            kotlin(
                "source.kt",
                """
          package test
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          private class PrivateClass(val a: Int)
          """))
    assertThat(result.exitCode).isEqualTo(COMPILATION_ERROR)
    assertThat(result.messages)
        .contains("@JsonClass can't be applied to test.PrivateClass: must be internal or public")
  }

  @Test
  fun objectDeclarationsNotSupported() {
    val result =
        compile(
            kotlin(
                "source.kt",
                """
          package test
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          object ObjectDeclaration {
            var a = 5
          }
          """))
    assertThat(result.exitCode).isEqualTo(COMPILATION_ERROR)
    assertThat(result.messages)
        .contains("@JsonClass can't be applied to test.ObjectDeclaration: must be a Kotlin class")
  }

  @Test
  fun requiredTransientConstructorParameterFails() {
    val result =
        compile(
            kotlin(
                "source.kt",
                """
          package test
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          class RequiredTransientConstructorParameter(@Transient var a: Int)
          """))
    assertThat(result.exitCode).isEqualTo(COMPILATION_ERROR)
    assertThat(result.messages).contains("No default value for transient/ignored property a")
  }

  @Test
  fun requiredIgnoredConstructorParameterFails() {
    val result =
        compile(
            kotlin(
                "source.kt",
                """
          package test
          import com.squareup.moshi.Json
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          class RequiredTransientConstructorParameter(@Json(ignore = true) var a: Int)
          """))
    assertThat(result.exitCode).isEqualTo(COMPILATION_ERROR)
    assertThat(result.messages).contains("No default value for transient/ignored property a")
  }

  @Test
  fun nonPropertyConstructorParameter() {
    val result =
        compile(
            kotlin(
                "source.kt",
                """
          package test
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          class NonPropertyConstructorParameter(a: Int, val b: Int)
          """))
    assertThat(result.exitCode).isEqualTo(COMPILATION_ERROR)
    assertThat(result.messages).contains("No property for required constructor parameter a")
  }

  @Ignore
  @Test
  fun badGeneratedAnnotation() {
    val result =
        prepareCompilation(
                "javax.annotation.GeneratedBlerg",
                kotlin(
                    "source.kt",
                    """
          package test
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          data class Foo(val a: Int)
          """))
            .compile()
    assertThat(result.messages).contains("Invalid option value for TODO")
  }

  @Test
  fun multipleErrors() {
    val result =
        compile(
            kotlin(
                "source.kt",
                """
          package test
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          class Class1(private var a: Int, private var b: Int)

          @JsonClass(generateAdapter = true)
          class Class2(private var c: Int)
          """))
    assertThat(result.exitCode).isEqualTo(COMPILATION_ERROR)
    assertThat(result.messages).contains("property a is not visible")
    assertThat(result.messages).contains("property c is not visible")
  }

  @Test
  fun extendPlatformType() {
    val result =
        compile(
            kotlin(
                "source.kt",
                """
          package test
          import com.squareup.moshi.JsonClass
          import java.util.Date

          @JsonClass(generateAdapter = true)
          class ExtendsPlatformClass(var a: Int) : Date()
          """))
    assertThat(result.messages).contains("supertype java.util.Date is not a Kotlin type")
  }

  @Test
  fun extendJavaType() {
    val result =
        compile(
            kotlin(
                "source.kt",
                """
          package test
          import com.squareup.moshi.JsonClass
          import com.squareup.moshi.kotlin.codegen.JavaSuperclass

          @JsonClass(generateAdapter = true)
          class ExtendsJavaType(var b: Int) : JavaSuperclass()
          """),
            SourceFile.java(
                "JavaSuperclass.java",
                """
        package com.squareup.moshi.kotlin.codegen;
        public class JavaSuperclass {
          public int a = 1;
        }
        """))
    assertThat(result.exitCode).isEqualTo(COMPILATION_ERROR)
    assertThat(result.messages)
        .contains("supertype com.squareup.moshi.kotlin.codegen.JavaSuperclass is not a Kotlin type")
  }

  @Test
  fun nonFieldApplicableQualifier() {
    val result =
        compile(
            kotlin(
                "source.kt",
                """
          package test
          import com.squareup.moshi.JsonClass
          import com.squareup.moshi.JsonQualifier
          import kotlin.annotation.AnnotationRetention.RUNTIME
          import kotlin.annotation.AnnotationTarget.PROPERTY
          import kotlin.annotation.Retention
          import kotlin.annotation.Target

          @Retention(RUNTIME)
          @Target(PROPERTY)
          @JsonQualifier
          annotation class UpperCase

          @JsonClass(generateAdapter = true)
          class ClassWithQualifier(@UpperCase val a: Int)
          """))
    // We instantiate directly, no FIELD site target necessary
    assertThat(result.exitCode).isEqualTo(OK)
  }

  @Test
  fun nonRuntimeQualifier() {
    val result =
        compile(
            kotlin(
                "source.kt",
                """
          package test
          import com.squareup.moshi.JsonClass
          import com.squareup.moshi.JsonQualifier
          import kotlin.annotation.AnnotationRetention.BINARY
          import kotlin.annotation.AnnotationTarget.FIELD
          import kotlin.annotation.AnnotationTarget.PROPERTY
          import kotlin.annotation.Retention
          import kotlin.annotation.Target

          @Retention(BINARY)
          @Target(PROPERTY, FIELD)
          @JsonQualifier
          annotation class UpperCase

          @JsonClass(generateAdapter = true)
          class ClassWithQualifier(@UpperCase val a: Int)
          """))
    assertThat(result.exitCode).isEqualTo(COMPILATION_ERROR)
    assertThat(result.messages).contains("JsonQualifier @UpperCase must have RUNTIME retention")
  }

  @Test
  fun `TypeAliases with the same backing type should share the same adapter`() {
    val result =
        compile(
            kotlin(
                "source.kt",
                """
          package test
          import com.squareup.moshi.JsonClass

          typealias FirstName = String
          typealias LastName = String

          @JsonClass(generateAdapter = true)
          data class Person(val firstName: FirstName, val lastName: LastName, val hairColor: String)
          """))
    assertThat(result.exitCode).isEqualTo(OK)

    // We're checking here that we only generate one `stringAdapter` that's used for both the
    // regular string properties as well as the the aliased ones.
    // TODO loading compiled classes from results not supported in KSP yet
    //    val adapterClass = result.classLoader.loadClass("PersonJsonAdapter").kotlin
    //    assertThat(adapterClass.declaredMemberProperties.map { it.returnType }).containsExactly(
    //      JsonReader.Options::class.createType(),
    //      JsonAdapter::class.parameterizedBy(String::class)
    //    )
  }

  private fun prepareCompilation(vararg sourceFiles: SourceFile): KotlinCompilation {
    return prepareCompilation(null, *sourceFiles)
  }

  private fun prepareCompilation(
      generatedAnnotation: String? = null,
      vararg sourceFiles: SourceFile
  ): KotlinCompilation {
    return KotlinCompilation().apply {
      workingDir = temporaryFolder.root
      compilerPlugins = listOf(MoshiComponentRegistrar())
      val processor = MoshiCommandLineProcessor()
      commandLineProcessors = listOf(processor)
      pluginOptions =
          buildList {
            add(processor.option(KEY_ENABLED, "true"))
            if (generatedAnnotation != null) {
              processor.option(KEY_GENERATED_ANNOTATION, generatedAnnotation)
            }
          }
      inheritClassPath = true
      sources = sourceFiles.asList()
      verbose = false
      jvmTarget = JvmTarget.fromString(System.getenv()["ci_java_version"] ?: "11")!!.description
    }
  }

  private fun CommandLineProcessor.option(key: Any, value: Any?): PluginOption {
    return PluginOption(pluginId, key.toString(), value.toString())
  }

  private fun compile(vararg sourceFiles: SourceFile): KotlinCompilation.Result {
    return compile(null, *sourceFiles)
  }

  private fun compile(
      generatedAnnotation: String? = null,
      vararg sourceFiles: SourceFile
  ): KotlinCompilation.Result {
    return prepareCompilation(generatedAnnotation, *sourceFiles).compile()
  }
}
