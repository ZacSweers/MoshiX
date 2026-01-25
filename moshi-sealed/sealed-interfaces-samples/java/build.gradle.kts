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
