// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.sealed.annotations

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS

/**
 * Use this annotation to specify specific `object` subtype to be the default instance.
 *
 * Only one of [DefaultObject], [FallbackJsonAdapter], or [DefaultNull] may be used on a
 * moshi-sealed class.
 */
@Target(CLASS) @Retention(RUNTIME) public annotation class DefaultObject
