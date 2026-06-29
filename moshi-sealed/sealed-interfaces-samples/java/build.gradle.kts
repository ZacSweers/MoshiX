// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
plugins { `java-library` }

val generatedAnnotation = "javax.annotation.processing.Generated"

dependencies {
  // TODO add when we support it in the processor
  //  annotationProcessor(project(":moshi-sealed:codegen"))
  //  testAnnotationProcessor(project(":moshi-sealed:codegen"))
  api(libs.moshi)
  implementation(project(":moshi-sealed:runtime"))
  //  implementation(project(":moshi-sealed:reflect"))
  testImplementation(project(":moshi-sealed:java-sealed-reflect"))
  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.guava)
}

// ksp {
//  arg("moshi.generated", generatedAnnotation)
// }
