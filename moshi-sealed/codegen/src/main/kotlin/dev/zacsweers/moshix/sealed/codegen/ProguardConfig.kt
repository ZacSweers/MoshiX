/*
 * Copyright (C) 2020 Zac Sweers
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
package dev.zacsweers.moshix.sealed.codegen

import com.squareup.kotlinpoet.ClassName
import javax.annotation.processing.Filer
import javax.lang.model.element.Element
import javax.tools.StandardLocation

/**
 * Represents a proguard configuration for a given spec. This covers two main areas:
 * - Keeping the target class name to Moshi's reflective lookup of the adapter.
 * - Keeping the generated adapter class name + public constructor for reflective lookup.
 *
 * Each rule is intended to be as specific and targeted as possible to reduce footprint, and each is
 * conditioned on usage of the original target type.
 *
 * To keep this processor as an ISOLATING incremental processor, we generate one file per target
 * class with a deterministic name (see [outputFile]) with an appropriate originating element.
 */
internal data class ProguardConfig(
  val targetClass: ClassName,
  val adapterName: String,
  val adapterConstructorParams: List<String>
) {
  private val outputFile = "META-INF/proguard/moshi-sealed-${targetClass.canonicalName}.pro"

  /** Writes this to `filer`. */
  fun writeTo(filer: Filer, vararg originatingElements: Element) {
    filer.createResource(StandardLocation.CLASS_OUTPUT, "", outputFile, *originatingElements)
      .openWriter()
      .use(::writeTo)
  }

  private fun writeTo(out: Appendable): Unit = out.run {
    //
    // -if class {the target class}
    // -keepnames class {the target class}
    // -if class {the target class}
    // -keep class {the generated adapter} {
    //    <init>(...);
    // }
    //
    val targetName = targetClass.reflectionName()
    val adapterCanonicalName = ClassName(targetClass.packageName, adapterName).canonicalName
    // Keep the class name for Moshi's reflective lookup based on it
    appendLine("-if class $targetName")
    appendLine("-keepnames class $targetName")

    appendLine("-if class $targetName")
    appendLine("-keep class $adapterCanonicalName {")
    // Keep the constructor for Moshi's reflective lookup
    val constructorArgs = adapterConstructorParams.joinToString(",")
    appendLine("    public <init>($constructorArgs);")
    appendLine("}")
  }
}
