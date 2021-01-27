/*
 * Copyright (c) 2021 Zac Sweers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
  `java-library`
}

val generatedAnnotation = "javax.annotation.processing.Generated"

dependencies {
  // TODO add when we suppose it in the processor
//  annotationProcessor(project(":moshi-sealed:codegen"))
//  testAnnotationProcessor(project(":moshi-sealed:codegen"))
  implementation(project(":moshi-records-reflect"))
  implementation(project(":moshi-sealed:java-sealed-reflect"))
  implementation(project(":moshi-sealed:runtime"))
  implementation(Dependencies.Moshi.adapters)
//  implementation(project(":moshi-sealed:reflect"))
  testImplementation(Dependencies.Testing.junit)
  testImplementation(Dependencies.Testing.truth)
}

//ksp {
//  arg("moshi.generated", generatedAnnotation)
//}
