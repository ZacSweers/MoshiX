package dev.zacsweers.moshix.ksp

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspSourcesDir
import org.junit.Ignore
import org.junit.Test
import java.io.File

@Ignore("Re-enable once there's a new kotlin compile testing")
class JsonClassSymbolProcessorTest {

  @Test
  fun smokeTest() {
    val source = SourceFile.kotlin("ErrorType.kt", """
      package test
      import com.squareup.moshi.JsonClass
      import dev.zacsweers.moshisealed.annotations.DefaultObject
      import dev.zacsweers.moshisealed.annotations.TypeLabel
      
      @JsonClass(generateAdapter = true)
      data class Example
    """)

    val compilation = KotlinCompilation().apply {
      sources = listOf(source)
      inheritClassPath = true
//      symbolProcessors = listOf(JsonClassSymbolProcessor())
    }
    val result = compilation.compile()
    assertThat(result.exitCode).isEqualTo(ExitCode.OK)
    val generatedSourcesDir = compilation.kspSourcesDir
    val generatedFile = File(generatedSourcesDir, "test/ErrorTypeJsonAdapter.kt")
    assertThat(generatedFile.exists()).isTrue()
    assertThat(generatedFile.readText()).isEqualTo("""
      
    """.trimIndent())
  }

}