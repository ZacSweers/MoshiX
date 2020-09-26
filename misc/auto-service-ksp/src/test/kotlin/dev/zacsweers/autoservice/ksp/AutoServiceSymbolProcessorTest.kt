package dev.zacsweers.autoservice.ksp

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspSourcesDir
import org.junit.Ignore
import org.junit.Test
import java.io.File

@Ignore("Disabled until kotlin-compile-testing updates")
class AutoServiceSymbolProcessorTest {
  @Test
  fun smokeTest() {
    val source = SourceFile.kotlin("CustomCallable.kt", """
      package test
      import com.google.auto.service.AutoService
      import java.util.concurrent.Callable
      
      @AutoService(Callable::class)
      class CustomCallable : Callable<String> {
        override fun call(): String = "Hello world!"
      }
    """)

    val compilation = KotlinCompilation().apply {
      sources = listOf(source)
      inheritClassPath = true
//      symbolProcessors = listOf(AutoServiceSymbolProcessor())
    }
    val result = compilation.compile()
    assertThat(result.exitCode).isEqualTo(ExitCode.OK)
    val generatedSourcesDir = compilation.kspSourcesDir
    val generatedFile = File(generatedSourcesDir,
      "resources/META-INF/services/java.util.concurrent.Callable")
    assertThat(generatedFile.exists()).isTrue()
    assertThat(generatedFile.readText()).isEqualTo("test.CustomCallable\n")
  }
}