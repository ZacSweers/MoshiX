import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.ksp)
  alias(libs.plugins.mavenPublish)
}

compileTestKotlin {
  compilerOptions { freeCompilerArgs.add("-opt-in=kotlin.ExperimentalStdlibApi") }
}

dependencies {
  implementation(libs.moshi)
  implementation(libs.kotlinx.collections.immutable)
  kspTest(libs.moshi.codegen)
  testImplementation(libs.moshi.kotlin)
  testImplementation(libs.okhttp)
  testImplementation(libs.okhttp.mockwebserver)
  testImplementation(libs.retrofit)
  testImplementation(libs.retrofit.moshi)
  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
