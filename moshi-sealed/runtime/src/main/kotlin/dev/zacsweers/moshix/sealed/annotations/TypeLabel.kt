// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.sealed.annotations

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS

/**
 * Use this annotation to specify the type label of a subtype.
 *
 * @property label the type label value.
 * @property alternateLabels an optional array of other values this type could be represented by.
 */
@Target(CLASS)
@Retention(RUNTIME)
public annotation class TypeLabel(val label: String, val alternateLabels: Array<String> = [])
