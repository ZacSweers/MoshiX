/*
 * Copyright (C) 2020 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.zacsweers.moshix

import com.squareup.moshi.Types
import java.lang.reflect.GenericArrayType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.javaType
import kotlin.reflect.typeOf

/** A map from primitive types to their corresponding wrapper types. */
private val PRIMITIVE_TO_WRAPPER_TYPE = LinkedHashMap<Class<*>, Class<*>>(16).apply {
  put(Boolean::class.javaPrimitiveType!!, Boolean::class.javaObjectType)
  put(Byte::class.javaPrimitiveType!!, Byte::class.javaObjectType)
  put(Char::class.javaPrimitiveType!!, Character::class.javaObjectType)
  put(Double::class.javaPrimitiveType!!, Double::class.javaObjectType)
  put(Float::class.javaPrimitiveType!!, Float::class.javaObjectType)
  put(Int::class.javaPrimitiveType!!, Integer::class.javaObjectType)
  put(Long::class.javaPrimitiveType!!, Long::class.javaObjectType)
  put(Short::class.javaPrimitiveType!!, Short::class.javaObjectType)
  put(Void::class.javaPrimitiveType!!, Void::class.javaObjectType)
}

@PublishedApi
internal fun <T> boxIfPrimitive(type: Class<T>): Class<T> {
  // cast is safe: long.class and Long.class are both of type Class<Long>
  @Suppress("UNCHECKED_CAST")
  return (PRIMITIVE_TO_WRAPPER_TYPE[type] ?: type) as Class<T>
}

/** Returns the raw [Class] type of this type. */
public val Type.rawType: Class<*> get() = Types.getRawType(this)

/**
 * Checks if [this] contains [T]. Returns the subset of [this] without [T], or null if
 * [this] does not contain [T].
 */
public inline fun <reified T : Annotation> Set<Annotation>.nextAnnotations(): Set<Annotation>? = Types.nextAnnotations(
  this, T::class.java)

/**
 * Returns a type that represents an unknown type that extends [T]. For example, if
 * [T] is [CharSequence], this returns `out CharSequence`. If
 * [T] is [Any], this returns `*`, which is shorthand for `out Any?`.
 */
@ExperimentalStdlibApi
public inline fun <reified T> subtypeOf(): WildcardType {
  var type = typeOf<T>().javaType
  if (type is Class<*>) {
    type = boxIfPrimitive(type)
  }
  return Types.subtypeOf(type)
}

/**
 * Returns a type that represents an unknown supertype of [T] bound. For example, if [T] is
 * [String], this returns `in String`.
 */
@ExperimentalStdlibApi
public inline fun <reified T> supertypeOf(): WildcardType {
  var type = typeOf<T>().javaType
  if (type is Class<*>) {
    type = boxIfPrimitive(type)
  }
  return Types.supertypeOf(type)
}

/** Returns a [GenericArrayType] with [this] as its [GenericArrayType.getGenericComponentType]. */
@ExperimentalStdlibApi
public fun KType.asArrayType(): GenericArrayType = javaType.asArrayType()

/** Returns a [GenericArrayType] with [this] as its [GenericArrayType.getGenericComponentType]. */
public fun KClass<*>.asArrayType(): GenericArrayType = java.asArrayType()

/** Returns a [GenericArrayType] with [this] as its [GenericArrayType.getGenericComponentType]. */
public fun Type.asArrayType(): GenericArrayType = Types.arrayOf(this)