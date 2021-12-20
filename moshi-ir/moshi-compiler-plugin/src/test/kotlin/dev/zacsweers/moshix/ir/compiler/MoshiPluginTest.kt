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
import com.tschuchort.compiletesting.PluginOption
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.SourceFile.Companion.kotlin
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.JvmTarget
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MoshiPluginTest {

  @Rule @JvmField var temporaryFolder: TemporaryFolder = TemporaryFolder()

  @Test
  fun simple() {
    val result =
        compile(
            kotlin(
                "SimpleClass.kt",
                """
          package dev.zacsweers.moshix.ir.compiler.test

          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          data class SimpleClass<T>(val a: Int, val b: T)
          """))
    // Kotlin reports an error message from IR as an internal error for some reason, so we just
    // check "not ok"
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
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
          listOf(
              processor.option(KEY_ENABLED, "true"),
              processor.option(
                  KEY_GENERATED_ANNOTATION, generatedAnnotation ?: "javax.annotation.Generated"),
          )
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
