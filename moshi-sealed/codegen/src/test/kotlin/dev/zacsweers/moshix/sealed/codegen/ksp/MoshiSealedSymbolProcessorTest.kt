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
package dev.zacsweers.moshix.sealed.codegen.ksp

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.CompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.SourceFile.Companion.kotlin
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.useKsp2
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class MoshiSealedSymbolProcessorProviderTest(private val useKSP2: Boolean) {

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "useKSP2={0}")
    fun data(): Collection<Array<Any>> {
      return listOf(arrayOf(true), arrayOf(false))
    }
  }

  @Test
  fun smokeTest() {
    val source =
      kotlin(
        "BaseType.kt",
        """
      package test
      import com.squareup.moshi.JsonClass
      import dev.zacsweers.moshix.sealed.annotations.TypeLabel
      import dev.zacsweers.moshix.sealed.annotations.NestedSealed

      @JsonClass(generateAdapter = true, generator = "sealed:type")
      sealed class BaseType {
        @TypeLabel("a", ["aa"])
        class TypeA : BaseType()
        @TypeLabel("b")
        class TypeB : BaseType()
        @NestedSealed
        sealed class TypeC : BaseType() {
          @TypeLabel("c")
          class TypeCImpl : TypeC()
        }
      }
    """,
      )

    val compilation = prepareCompilation(source)
    val result = compilation.compile()
    assertThat(result.exitCode).isEqualTo(ExitCode.OK)
    val generatedSourcesDir = compilation.kspSourcesDir
    val generatedAdapter = File(generatedSourcesDir, "kotlin/test/BaseTypeJsonAdapter.kt")
    assertThat(generatedAdapter.exists()).isTrue()
    // language=kotlin
    assertThat(generatedAdapter.readText().trim())
      .isEqualTo(
        """
        // Code generated by moshi-sealed. Do not edit.
        package test

        import com.squareup.moshi.JsonAdapter
        import com.squareup.moshi.JsonReader
        import com.squareup.moshi.JsonWriter
        import com.squareup.moshi.Moshi
        import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
        import kotlin.Suppress
        import kotlin.collections.emptySet

        @Suppress("DEPRECATION", "unused", "ClassName", "REDUNDANT_PROJECTION", "RedundantExplicitType", "LocalVariableName", "RedundantVisibilityModifier")
        public class BaseTypeJsonAdapter(
          moshi: Moshi,
        ) : JsonAdapter<BaseType>() {
          @Suppress("UNCHECKED_CAST")
          private val runtimeAdapter: JsonAdapter<BaseType> =
              PolymorphicJsonAdapterFactory.of(BaseType::class.java, "type")
                .withSubtype(BaseType.TypeA::class.java, "a")
                .withSubtype(BaseType.TypeA::class.java, "aa")
                .withSubtype(BaseType.TypeB::class.java, "b")
                .withSubtype(BaseType.TypeC.TypeCImpl::class.java, "c")
                .create(BaseType::class.java, emptySet(), moshi) as JsonAdapter<BaseType>

          override fun fromJson(reader: JsonReader): BaseType? = runtimeAdapter.fromJson(reader)

          override fun toJson(writer: JsonWriter, value_: BaseType?) {
            runtimeAdapter.toJson(writer, value_)
          }
        }
        """.trimIndent()
      )
  }

  @Test
  fun duplicateLabels() {
    val source =
      kotlin(
        "BaseType.kt",
        """
      package test
      import com.squareup.moshi.JsonClass
      import dev.zacsweers.moshix.sealed.annotations.TypeLabel

      @JsonClass(generateAdapter = true, generator = "sealed:type")
      sealed class BaseType {
        @TypeLabel("a")
        class TypeA : BaseType()
        @TypeLabel("a")
        class TypeB : BaseType()
      }
    """,
      )

    val result = compile(source)
    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("Duplicate label")
  }

  @Test
  fun duplicateAlternateLabels() {
    val source =
      kotlin(
        "BaseType.kt",
        """
      package test
      import com.squareup.moshi.JsonClass
      import dev.zacsweers.moshix.sealed.annotations.TypeLabel

      @JsonClass(generateAdapter = true, generator = "sealed:type")
      sealed class BaseType {
        @TypeLabel("a", alternateLabels = ["aa"])
        class TypeA : BaseType()
        @TypeLabel("b", alternateLabels = ["aa"])
        class TypeB : BaseType()
      }
    """,
      )

    val result = compile(source)
    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("Duplicate alternate label")
  }

  @Test
  fun genericSubTypes() {
    val source =
      kotlin(
        "BaseType.kt",
        """
      package test
      import com.squareup.moshi.JsonClass
      import dev.zacsweers.moshix.sealed.annotations.TypeLabel

      @JsonClass(generateAdapter = true, generator = "sealed:type")
      sealed class BaseType<T> {
        @TypeLabel("a")
        class TypeA : BaseType<String>()
        @TypeLabel("b")
        class TypeB<T> : BaseType<T>()
      }
    """,
      )

    val result = compile(source)
    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("Moshi-sealed subtypes cannot be generic.")
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
    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
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
    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
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
    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
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
    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.messages)
      .contains(
        "Fallback adapter type BaseTypeFallback and its primary constructor must be visible from BaseType"
      )
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
    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.messages)
      .contains("Fallback adapter type's primary constructor can only have a Moshi parameter")
  }

  @Test
  fun objectAdapters() {
    val source =
      kotlin(
        "BaseType.kt",
        """
      package test
      import com.squareup.moshi.JsonClass
      import dev.zacsweers.moshix.sealed.annotations.TypeLabel

      @JsonClass(generateAdapter = true, generator = "sealed:type")
      sealed class BaseType {
        @TypeLabel("a")
        object TypeA : BaseType()
        @TypeLabel("b")
        object TypeB : BaseType()
      }
    """,
      )

    val compilation = prepareCompilation(source)
    val result = compilation.compile()
    assertThat(result.exitCode).isEqualTo(ExitCode.OK)
    val generatedSourcesDir = compilation.kspSourcesDir
    val generatedFile = File(generatedSourcesDir, "kotlin/test/BaseTypeJsonAdapter.kt")
    assertThat(generatedFile.exists()).isTrue()
    // language=kotlin
    assertThat(generatedFile.readText().trim())
      .isEqualTo(
        """
        // Code generated by moshi-sealed. Do not edit.
        package test

        import com.squareup.moshi.JsonAdapter
        import com.squareup.moshi.JsonReader
        import com.squareup.moshi.JsonWriter
        import com.squareup.moshi.Moshi
        import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
        import dev.zacsweers.moshix.`sealed`.runtime.`internal`.ObjectJsonAdapter
        import kotlin.ExperimentalStdlibApi
        import kotlin.OptIn
        import kotlin.Suppress
        import kotlin.collections.emptySet

        @Suppress("DEPRECATION", "unused", "ClassName", "REDUNDANT_PROJECTION", "RedundantExplicitType", "LocalVariableName", "RedundantVisibilityModifier")
        public class BaseTypeJsonAdapter(
          moshi: Moshi,
        ) : JsonAdapter<BaseType>() {
          private val runtimeAdapter: JsonAdapter<BaseType>

          init {
            val moshi_ = moshi.newBuilder()
                    .add(BaseType.TypeA::class.java, ObjectJsonAdapter(BaseType.TypeA))
                .add(BaseType.TypeB::class.java, ObjectJsonAdapter(BaseType.TypeB))
                .build()
            @Suppress("UNCHECKED_CAST")
            @OptIn(ExperimentalStdlibApi::class)
            runtimeAdapter = PolymorphicJsonAdapterFactory.of(BaseType::class.java, "type")
                  .withSubtype(BaseType.TypeA::class.java, "a")
                  .withSubtype(BaseType.TypeB::class.java, "b")
                  .create(BaseType::class.java, emptySet(), moshi_) as JsonAdapter<BaseType>
          }

          override fun fromJson(reader: JsonReader): BaseType? = runtimeAdapter.fromJson(reader)

          override fun toJson(writer: JsonWriter, value_: BaseType?) {
            runtimeAdapter.toJson(writer, value_)
          }
        }
        """.trimIndent()
      )
  }

  @Test
  fun separateFiles() {
    val base =
      kotlin(
        "BaseType.kt",
        """
        package test

        import com.squareup.moshi.JsonClass

        @JsonClass(generateAdapter = true, generator = "sealed:type")
        sealed interface BaseType
      """,
      )

    val subType =
      kotlin(
        "SubType.kt",
        """
        package test

        import com.squareup.moshi.JsonClass
        import dev.zacsweers.moshix.sealed.annotations.TypeLabel

        @JsonClass(generateAdapter = true)
        @TypeLabel("a")
        data class SubType(val foo: String): BaseType""",
      )

    val compilation = prepareCompilation(base, subType)
    val result = compilation.compile()
    assertThat(result.exitCode).isEqualTo(ExitCode.OK)

    val generatedSourcesDir = compilation.kspSourcesDir
    val generatedAdapter = File(generatedSourcesDir, "kotlin/test/BaseTypeJsonAdapter.kt")
    assertThat(generatedAdapter.exists()).isTrue()

    // language=kotlin
    assertThat(generatedAdapter.readText().trim())
      .isEqualTo(
        """
        // Code generated by moshi-sealed. Do not edit.
        package test

        import com.squareup.moshi.JsonAdapter
        import com.squareup.moshi.JsonReader
        import com.squareup.moshi.JsonWriter
        import com.squareup.moshi.Moshi
        import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
        import kotlin.Suppress
        import kotlin.collections.emptySet

        @Suppress("DEPRECATION", "unused", "ClassName", "REDUNDANT_PROJECTION", "RedundantExplicitType", "LocalVariableName", "RedundantVisibilityModifier")
        public class BaseTypeJsonAdapter(
          moshi: Moshi,
        ) : JsonAdapter<BaseType>() {
          @Suppress("UNCHECKED_CAST")
          private val runtimeAdapter: JsonAdapter<BaseType> =
              PolymorphicJsonAdapterFactory.of(BaseType::class.java, "type")
                .withSubtype(SubType::class.java, "a")
                .create(BaseType::class.java, emptySet(), moshi) as JsonAdapter<BaseType>

          override fun fromJson(reader: JsonReader): BaseType? = runtimeAdapter.fromJson(reader)

          override fun toJson(writer: JsonWriter, value_: BaseType?) {
            runtimeAdapter.toJson(writer, value_)
          }
        }
        """.trimIndent()
      )
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
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
  }

  private fun prepareCompilation(vararg sourceFiles: SourceFile): KotlinCompilation =
    KotlinCompilation().apply {
      sources = sourceFiles.toList()
      inheritClassPath = true
      if (useKSP2) {
        useKsp2()
      } else {
        languageVersion = "1.9"
      }
      configureKsp(useKSP2) { symbolProcessorProviders += MoshiSealedSymbolProcessorProvider() }
    }

  private fun compile(vararg sourceFiles: SourceFile): CompilationResult {
    return prepareCompilation(*sourceFiles).compile()
  }
}
