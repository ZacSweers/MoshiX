import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish")
  id("com.google.devtools.ksp")
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    @Suppress("SuspiciousCollectionReassignment")
    freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
  }
}

dependencies {
//  compileOnly(kotlin("compiler"))
  compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.6.10")
  implementation("com.google.auto.service:auto-service-annotations:1.0.1")
  ksp("dev.zacsweers.autoservice:auto-service-ksp:1.0.0")

  testImplementation("org.jetbrains.kotlin:kotlin-reflect:1.6.10")
//  testImplementation(kotlin("compiler"))
  testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.6.10")
  testImplementation("com.github.tschuchortdev:kotlin-compile-testing:1.4.7")
  testImplementation("junit:junit:4.13.2")
  testImplementation("com.google.truth:truth:1.1.3")
  testImplementation("com.squareup.moshi:moshi:1.13.0")
}
