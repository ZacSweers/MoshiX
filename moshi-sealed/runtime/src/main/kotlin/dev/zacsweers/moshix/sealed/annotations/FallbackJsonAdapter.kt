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
