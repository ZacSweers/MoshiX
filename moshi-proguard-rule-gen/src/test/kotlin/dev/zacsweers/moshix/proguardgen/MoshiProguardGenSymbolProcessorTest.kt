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
package dev.zacsweers.moshix.proguardgen

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.SourceFile.Companion.kotlin
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.kspProcessorOptions
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.useKsp2
import dev.zacsweers.moshix.proguardgen.MoshiProguardGenSymbolProcessor.Companion.OPTION_GENERATE_MOSHI_CORE_PROGUARD_RULES
import dev.zacsweers.moshix.proguardgen.MoshiProguardGenSymbolProcessor.Companion.OPTION_GENERATE_PROGUARD_RULES
import java.io.File
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@OptIn(ExperimentalCompilerApi::class)
@RunWith(Parameterized::class)
class MoshiProguardGenSymbolProcessorTest(private val useKSP2: Boolean) {

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "useKSP2={0}")
    fun data(): Collection<Array<Any>> {
      return listOf(arrayOf(true), arrayOf(false))
    }
  }

  @Test
  fun `standard test with only sealed enabled`() {
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
    val proguardFiles = generatedSourcesDir.walkTopDown().filter { it.extension == "pro" }.toList()
    check(proguardFiles.isNotEmpty())
    for (generatedFile in proguardFiles) {
      generatedFile.assertInCorrectPath()
      when (generatedFile.nameWithoutExtension) {
        "moshi-test.BaseType" ->
          assertThat(generatedFile.readText().trimIndent())
            .isEqualTo(
              """
                  # Conditionally keep this adapter for every possible nested subtype that uses it.
                  -if class test.BaseType.TypeA
                  -keep class test.BaseTypeJsonAdapter {
                      public <init>(com.squareup.moshi.Moshi);
                  }
                  -if class test.BaseType.TypeB
                  -keep class test.BaseTypeJsonAdapter {
                      public <init>(com.squareup.moshi.Moshi);
                  }
                  -if class test.BaseType.TypeC
                  -keep class test.BaseTypeJsonAdapter {
                      public <init>(com.squareup.moshi.Moshi);
                  }
                  -if class test.BaseType.TypeC.TypeCImpl
                  -keep class test.BaseTypeJsonAdapter {
                      public <init>(com.squareup.moshi.Moshi);
                  }
                """
                .trimIndent()
            )
        else -> error("Unrecognized proguard file: $generatedFile")
      }
    }
  }

  @Test
  fun ensureNestedSealedClassesAreAdded() {
    val source =
      kotlin(
        "BaseType.kt",
        """
      package test
      import com.squareup.moshi.Json
      import com.squareup.moshi.JsonClass
      import dev.zacsweers.moshix.sealed.annotations.TypeLabel
      import dev.zacsweers.moshix.sealed.annotations.NestedSealed
      import dev.zacsweers.moshix.sealed.annotations.DefaultObject

      @JsonClass(generateAdapter = true, generator = "sealed:type")
        sealed class Message {

            @TypeLabel("success")
            @JsonClass(generateAdapter = true)
            data class Success(
                @Json(name = "value")
                val value: String
            ) : Message()

            @DefaultObject
            object Unknown : Message()
        }
    """,
      )

    val compilation = prepareCompilation(source)
    val result = compilation.compile()
    assertThat(result.exitCode).isEqualTo(ExitCode.OK)
    val generatedSourcesDir = compilation.kspSourcesDir
    val proguardFiles = generatedSourcesDir.walkTopDown().filter { it.extension == "pro" }.toList()
    check(proguardFiles.isNotEmpty())
    for (generatedFile in proguardFiles) {
      generatedFile.assertInCorrectPath()
      when (generatedFile.nameWithoutExtension) {
        "moshi-test.Message" ->
          assertThat(generatedFile.readText().trimIndent())
            .isEqualTo(
              """
                  # Conditionally keep this adapter for every possible nested subtype that uses it.
                  -if class test.Message.Success
                  -keep class test.MessageJsonAdapter {
                      public <init>(com.squareup.moshi.Moshi);
                  }
                  -if class test.Message.Unknown
                  -keep class test.MessageJsonAdapter {
                      public <init>(com.squareup.moshi.Moshi);
                  }
                """
                .trimIndent()
            )
        else -> error("Unrecognized proguard file: $generatedFile")
      }
    }
  }

  @Test
  fun disableProguardGeneration() {
    val source =
      kotlin(
        "BaseType.kt",
        """
      package test
      import com.squareup.moshi.JsonClass
      import dev.zacsweers.moshix.sealed.annotations.TypeLabel

      @JsonClass(generateAdapter = true, generator = "sealed:type")
      sealed class BaseType {
        @TypeLabel("a", ["aa"])
        class TypeA : BaseType()
        @TypeLabel("b")
        class TypeB : BaseType()
      }
    """,
      )

    val compilation =
      prepareCompilation(source) {
        kspProcessorOptions += mapOf(OPTION_GENERATE_PROGUARD_RULES to "false")
      }
    val result = compilation.compile()
    assertThat(result.exitCode).isEqualTo(ExitCode.OK)
    assertThat(result.generatedFiles.filter { it.extension == "pro" }).isEmpty()
  }

  @Test
  fun enableCoreProguardGeneration() {
    val source =
      kotlin(
        "BaseType.kt",
        """package test

import com.squareup.moshi.JsonClass
import dev.zacsweers.moshix.sealed.annotations.NestedSealed
import dev.zacsweers.moshix.sealed.annotations.TypeLabel

@JsonClass(generateAdapter = true, generator = "sealed:type")
sealed class BaseType {
  @TypeLabel("a", ["aa"]) class TypeA : BaseType()

  @TypeLabel("b") class TypeB : BaseType()

  @NestedSealed
  sealed class TypeC : BaseType() {
    @TypeLabel("c") class TypeCImpl : TypeC()
  }
}
""",
      )

    val compilation =
      prepareCompilation(source) {
        kspProcessorOptions += mapOf(OPTION_GENERATE_MOSHI_CORE_PROGUARD_RULES to "true")
      }
    val result = compilation.compile()
    assertThat(result.exitCode).isEqualTo(ExitCode.OK)
    val generatedSourcesDir = compilation.kspSourcesDir
    val proguardFiles = generatedSourcesDir.walkTopDown().filter { it.extension == "pro" }.toList()
    check(proguardFiles.isNotEmpty()) { "No generated proguard files found" }
    for (generatedFile in proguardFiles) {
      generatedFile.assertInCorrectPath()
      when (generatedFile.nameWithoutExtension) {
        "moshi-test.BaseType" ->
          assertThat(generatedFile.readText().trimIndent())
            .isEqualTo(
              """
                  -if class test.BaseType
                  -keepnames class test.BaseType
                  -if class test.BaseType
                  -keep class test.BaseTypeJsonAdapter {
                      public <init>(com.squareup.moshi.Moshi);
                  }

                  # Conditionally keep this adapter for every possible nested subtype that uses it.
                  -if class test.BaseType.TypeA
                  -keep class test.BaseTypeJsonAdapter {
                      public <init>(com.squareup.moshi.Moshi);
                  }
                  -if class test.BaseType.TypeB
                  -keep class test.BaseTypeJsonAdapter {
                      public <init>(com.squareup.moshi.Moshi);
                  }
                  -if class test.BaseType.TypeC
                  -keep class test.BaseTypeJsonAdapter {
                      public <init>(com.squareup.moshi.Moshi);
                  }
                  -if class test.BaseType.TypeC.TypeCImpl
                  -keep class test.BaseTypeJsonAdapter {
                      public <init>(com.squareup.moshi.Moshi);
                  }
                """
                .trimIndent()
            )
        else -> error("Unrecognized proguard file: $generatedFile")
      }
    }
  }

  private fun prepareCompilation(
    vararg sourceFiles: SourceFile,
    block: KotlinCompilation.() -> Unit = {},
  ): KotlinCompilation =
    KotlinCompilation().apply {
      sources = sourceFiles.toList()
      inheritClassPath = true
      if (useKSP2) {
        useKsp2()
      } else {
        languageVersion = "1.9"
      }
      configureKsp(useKSP2) {
        symbolProcessorProviders += MoshiProguardGenSymbolProcessor.Provider()
      }
      block()
    }

  private fun File.assertInCorrectPath() {
    // Ensure the proguard file is in the right place
    // https://github.com/ZacSweers/MoshiX/issues/461
    check(absolutePath.contains("META-INF/proguard/"))
  }
}
