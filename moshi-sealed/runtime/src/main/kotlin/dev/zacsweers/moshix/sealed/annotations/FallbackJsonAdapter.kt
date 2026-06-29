// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.sealed.annotations

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.reflect.KClass

/**
 * Use this annotation to specify specific fallback adapter to use. This is a proxy to
 * [PolymorphicJsonAdapterFactory.withFallbackJsonAdapter].
 *
 * The referenced class _must_ have a single primary constructor that accepts either a [Moshi]
 * parameter or no parameters.
 *
 * Only one of [DefaultObject], [FallbackJsonAdapter], or [DefaultNull] may be used on a
 * moshi-sealed class.
 */
@Target(CLASS)
@Retention(RUNTIME)
public annotation class FallbackJsonAdapter(val value: KClass<out JsonAdapter<*>>)
