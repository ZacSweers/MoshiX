// Copyright (C) 2021 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
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
  /**
   * Set this property to false to disable auto-application of the Moshi dependency. Enabled by
   * default.
   */
  val applyMoshiDependency: Property<Boolean> =
    objects.property(Boolean::class.java).convention(true)
}
