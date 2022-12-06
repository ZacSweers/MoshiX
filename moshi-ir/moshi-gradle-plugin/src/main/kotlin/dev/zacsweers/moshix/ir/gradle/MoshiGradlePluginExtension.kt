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
package dev.zacsweers.moshix.ir.gradle

import javax.inject.Inject
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

abstract class MoshiPluginExtension @Inject constructor(objects: ObjectFactory) {
  val enabled: Property<Boolean> = objects.property(Boolean::class.javaObjectType).convention(true)
  /** Enables debug logging. Useful mostly for helping report bugs/issues. */
  val debug: Property<Boolean> = objects.property(Boolean::class.javaObjectType).convention(false)
  /**
   * Define a custom generated annotation.
   *
   * Note that this must be in the format of a string where packages are delimited by '/' and
   * classes by '.', e.g. "kotlin/Map.Entry"
   *
   * **Note:** this is not currently implemented yet
   */
  val generatedAnnotation: Property<String> = objects.property(String::class.java)
  /** Enables moshi-sealed code gen. Disabled by default. */
  val enableSealed: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
  /** Enables automatic generation of proguard rules. Enabled by default. */
  val generateProguardRules: Property<Boolean> =
    objects.property(Boolean::class.java).convention(true)
}
