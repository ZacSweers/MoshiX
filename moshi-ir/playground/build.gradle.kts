plugins {
  kotlin("jvm")
  id("dev.zacsweers.moshix")
}

dependencies {
  testImplementation("junit:junit:4.13.2")
  testImplementation("com.google.truth:truth:1.1.3")
  testImplementation("com.squareup.moshi:moshi:1.13.0")
  testImplementation(kotlin("reflect"))
}

configurations.configureEach {
  resolutionStrategy.dependencySubstitution {
    substitute(module("dev.zacsweers.moshix:moshi-compiler-plugin"))
      .using(project(":moshi-ir:moshi-compiler-plugin"))
  }
}