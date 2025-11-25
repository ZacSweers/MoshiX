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
import com.tschuchort.compiletesting.CompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import com.tschuchort.compiletesting.PluginOption
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.SourceFile.Companion.kotlin
import java.io.File
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.JvmTarget
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MoshiIrVisitorTest {

  @Rule @JvmField var temporaryFolder: TemporaryFolder = TemporaryFolder()

  private lateinit var compilationDir: File
  private lateinit var resourcesDir: File

  @Before
  fun setup() {
    compilationDir = temporaryFolder.newFolder("compilationDir")
    resourcesDir = temporaryFolder.newFolder("resourcesDir")
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
          """,
        )
      )
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
          """,
        )
      )
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
          """,
        )
      )
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
          """,
        )
      )
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
          """,
        )
      )
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
          """,
        )
      )
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
          """,
        )
      )
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
          """,
        )
      )
    assertThat(result.exitCode).isEqualTo(COMPILATION_ERROR)
    assertThat(result.messages)
      .contains("@JsonClass can't be applied to test.Outer.InnerClass: must not be an inner class")
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
          """,
        )
      )
    assertThat(result.exitCode).isEqualTo(COMPILATION_ERROR)
    assertThat(result.messages)
      .contains(
        "@JsonClass with 'generateAdapter = \"true\"' can't be applied to test.KotlinEnum: code gen for enums is not supported or necessary"
      )
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
          """,
        )
      )
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
          """,
        )
      )
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
          """,
        )
      )
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
          """,
        )
      )
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
          """,
        )
      )
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
          """,
        )
      )
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
          """,
          ),
        )
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
          """,
        )
      )
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
          """,
        )
      )
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
          """,
        ),
        SourceFile.java(
          "JavaSuperclass.java",
          """
        package com.squareup.moshi.kotlin.codegen;
        public class JavaSuperclass {
          public int a = 1;
        }
        """,
        ),
      )
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
          """,
        )
      )
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
          """,
        )
      )
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
          """,
        )
      )
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

  @Test
  fun nullAndFallback() {
    val source =
      kotlin(
        "BaseType.kt",
        """
      package test
      import com.squareup.moshi.JsonClass
      import dev.zacsweers.moshix.sealed.annotations.TypeLabel
      import dev.zacsweers.moshix.sealed.annotations.DefaultNull
      import dev.zacsweers.moshix.sealed.annotations.FallbackJsonAdapter
      import com.squareup.moshi.JsonReader
      import com.squareup.moshi.JsonWriter
      import com.squareup.moshi.JsonAdapter

      class BaseTypeFallback : JsonAdapter<String>() {
        override fun fromJson(reader: JsonReader): String? {
          return null
        }

        override fun toJson(writer: JsonWriter, value: String?) {
        }
      }

      @FallbackJsonAdapter(BaseTypeFallback::class)
      @DefaultNull
      @JsonClass(generateAdapter = true, generator = "sealed:type")
      sealed class BaseType {
        @TypeLabel("a")
        class TypeA : BaseType()
      }
    """,
      )

    val result = compile(source)
    assertThat(result.exitCode).isEqualTo(COMPILATION_ERROR)
    assertThat(result.messages)
      .contains("Only one of @DefaultNull or @FallbackJsonAdapter can be used at a time")
  }

  @Test
  fun nullAndDefaultObject() {
    val source =
      kotlin(
        "BaseType.kt",
        """
      package test
      import com.squareup.moshi.JsonClass
      import dev.zacsweers.moshix.sealed.annotations.TypeLabel
      import dev.zacsweers.moshix.sealed.annotations.DefaultNull
      import dev.zacsweers.moshix.sealed.annotations.DefaultObject

      @DefaultNull
      @JsonClass(generateAdapter = true, generator = "sealed:type")
      sealed class BaseType {
        @TypeLabel("a")
        class TypeA : BaseType()
        @DefaultObject
        object TypeB : BaseType()
      }
    """,
      )

    val result = compile(source)
    assertThat(result.exitCode).isEqualTo(COMPILATION_ERROR)
    assertThat(result.messages).contains("Cannot have both @DefaultNull and @DefaultObject")
  }

  @Test
  fun defaultObjectAndFallbackAdapter() {
    val source =
      kotlin(
        "BaseType.kt",
        """
      package test
      import com.squareup.moshi.JsonClass
      import dev.zacsweers.moshix.sealed.annotations.TypeLabel
      import dev.zacsweers.moshix.sealed.annotations.DefaultObject
      import dev.zacsweers.moshix.sealed.annotations.FallbackJsonAdapter
      import com.squareup.moshi.JsonReader
      import com.squareup.moshi.JsonWriter
      import com.squareup.moshi.JsonAdapter

      class BaseTypeFallback : JsonAdapter<String>() {
        override fun fromJson(reader: JsonReader): String? {
          return null
        }

        override fun toJson(writer: JsonWriter, value: String?) {
        }
      }

      @FallbackJsonAdapter(BaseTypeFallback::class)
      @JsonClass(generateAdapter = true, generator = "sealed:type")
      sealed class BaseType {
        @TypeLabel("a")
        class TypeA : BaseType()
        @DefaultObject
        object TypeB : BaseType()
      }
    """,
      )

    val result = compile(source)
    assertThat(result.exitCode).isEqualTo(COMPILATION_ERROR)
    assertThat(result.messages)
      .contains(
        "Only one of @DefaultObject, @DefaultNull, or @FallbackJsonAdapter can be used at a time"
      )
  }

  @Test
  fun invisibleFallbackAdapterConstructor() {
    val source =
      kotlin(
        "BaseType.kt",
        """
      package test
      import com.squareup.moshi.JsonClass
      import dev.zacsweers.moshix.sealed.annotations.TypeLabel
      import dev.zacsweers.moshix.sealed.annotations.DefaultObject
      import dev.zacsweers.moshix.sealed.annotations.FallbackJsonAdapter
      import com.squareup.moshi.JsonReader
      import com.squareup.moshi.JsonWriter
      import com.squareup.moshi.JsonAdapter

      class BaseTypeFallback private constructor() : JsonAdapter<String>() {
        override fun fromJson(reader: JsonReader): String? {
          return null
        }

        override fun toJson(writer: JsonWriter, value: String?) {
        }
      }

      @FallbackJsonAdapter(BaseTypeFallback::class)
      @JsonClass(generateAdapter = true, generator = "sealed:type")
      sealed class BaseType {
        @TypeLabel("a")
        class TypeA : BaseType()
        @DefaultObject
        object TypeB : BaseType()
      }
    """,
      )

    val result = compile(source)
    assertThat(result.exitCode).isEqualTo(COMPILATION_ERROR)
    assertThat(result.messages).contains("Visibility must be one of public or internal. Is private")
  }

  @Test
  fun invalidConstructorParam() {
    val source =
      kotlin(
        "BaseType.kt",
        """
      package test
      import com.squareup.moshi.JsonClass
      import dev.zacsweers.moshix.sealed.annotations.TypeLabel
      import dev.zacsweers.moshix.sealed.annotations.FallbackJsonAdapter
      import dev.zacsweers.moshix.sealed.runtime.internal.ObjectJsonAdapter

      @FallbackJsonAdapter(ObjectJsonAdapter::class)
      @JsonClass(generateAdapter = true, generator = "sealed:type")
      sealed class BaseType {
        @TypeLabel("a")
        class TypeA : BaseType()
      }
    """,
      )

    val result = compile(source)
    assertThat(result.exitCode).isEqualTo(COMPILATION_ERROR)
    assertThat(result.messages)
      .contains("Fallback adapter type's primary constructor can only have a Moshi parameter")
  }

  @Test
  fun correctConstructorParam() {
    val source =
      kotlin(
        "BaseType.kt",
        """
      package test
      import com.squareup.moshi.JsonClass
      import com.squareup.moshi.Moshi
      import com.squareup.moshi.JsonAdapter
      import com.squareup.moshi.adapter
      import com.squareup.moshi.JsonWriter
      import com.squareup.moshi.JsonReader
      import dev.zacsweers.moshix.sealed.annotations.TypeLabel
      import dev.zacsweers.moshix.sealed.annotations.FallbackJsonAdapter
      import dev.zacsweers.moshix.sealed.runtime.internal.ObjectJsonAdapter

      @OptIn(ExperimentalStdlibApi::class)
      @FallbackJsonAdapter(MessageWithFallbackAdapter.SuccessAdapter::class)
      @JsonClass(generateAdapter = true, generator = "sealed:type")
      sealed class MessageWithFallbackAdapter {

        @TypeLabel("success", ["successful"])
        @JsonClass(generateAdapter = true)
        data class Success(val value: String) : MessageWithFallbackAdapter()

        @TypeLabel("error")
        @JsonClass(generateAdapter = true)
        data class Error(val error_logs: Map<String, Any>) : MessageWithFallbackAdapter()

        class SuccessAdapter(moshi: Moshi) : JsonAdapter<MessageWithFallbackAdapter>() {
          private val delegate = moshi.adapter<Success>()

          override fun fromJson(reader: JsonReader): MessageWithFallbackAdapter? {
            return delegate.fromJson(reader)
          }

          override fun toJson(writer: JsonWriter, value: MessageWithFallbackAdapter?) {
            throw NotImplementedError()
          }
        }
      }
    """,
      )

    val result = compile(source)
    assertThat(result.exitCode).isEqualTo(OK)
  }

  // Covers cases where a nested sealed interface is also implemented by a subtype that implements
  // the super type
  @Test
  fun nestedSealedWithCommonSubtypes() {
    val source =
      kotlin(
        "BaseType.kt",
        """
      package test
      import com.squareup.moshi.JsonClass
      import dev.zacsweers.moshix.sealed.annotations.TypeLabel
      import dev.zacsweers.moshix.sealed.annotations.NestedSealed

      @JsonClass(generateAdapter = true, generator = "sealed:type")
      sealed interface Foo {

        @NestedSealed
        sealed interface SuperFoo : Foo

        @JsonClass(generateAdapter = true)
        @TypeLabel("real")
        data class RealFoo(val value: String) : SuperFoo, Foo
      }
    """,
      )

    val result = compile(source)
    assertThat(result.exitCode).isEqualTo(OK)
  }

  private fun prepareCompilation(
    generatedAnnotation: String? = null,
    vararg sourceFiles: SourceFile,
  ): KotlinCompilation {
    return KotlinCompilation().apply {
      workingDir = compilationDir
      compilerPluginRegistrars = listOf(MoshiComponentRegistrar())
      val processor = MoshiCommandLineProcessor()
      commandLineProcessors = listOf(processor)
      pluginOptions = buildList {
        add(processor.option(MoshiCommandLineProcessor.OPTION_ENABLED, "true"))
        // Enable when needed for extra debugging
        add(processor.option(MoshiCommandLineProcessor.OPTION_DEBUG, "false"))
        add(processor.option(MoshiCommandLineProcessor.OPTION_ENABLE_SEALED, "true"))
        if (generatedAnnotation != null) {
          processor.option(
            MoshiCommandLineProcessor.OPTION_GENERATED_ANNOTATION,
            generatedAnnotation,
          )
        }
      }
      inheritClassPath = true
      sources = sourceFiles.asList()
      verbose = false
      jvmTarget = JvmTarget.fromString(System.getProperty("moshix.jvmTarget"))!!.description
    }
  }

  private fun CommandLineProcessor.option(key: CliOption, value: Any?): PluginOption {
    return PluginOption(pluginId, key.optionName, value.toString())
  }

  private fun compile(vararg sourceFiles: SourceFile): CompilationResult {
    return prepareCompilation(null, *sourceFiles).compile()
  }
}
