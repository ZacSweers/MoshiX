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
package dev.zacsweers.moshix.sealed.sample

import com.squareup.moshi.JsonClass
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import dev.zacsweers.moshix.sealed.annotations.TypeLabel

/**
 * `object` types are useful in cases when receiving empty json objects (`{}`) or cases where its
 * type can be inferred by some delegating adapter that peeks its keys. They should only be used for
 * types that are sentinels and do not actually contain meaningful data.
 *
 * In this example, we have a [FunctionSpec] that defines the signature of a function, and the below
 * [Type] representations that can be used for its return type and parameter types. These are all
 * `object` types, so any contents are skipped in its serialization and only its `type` key is read
 * by the [PolymorphicJsonAdapterFactory] to determine its type.
 */
@JsonClass(generateAdapter = true, generator = "sealed:type")
sealed class Type(val type: String) {
  @TypeLabel("void") object VoidType : Type("void")
  @TypeLabel("boolean") object BooleanType : Type("boolean")
  @TypeLabel("int") object IntType : Type("int")

  override fun toString() = type
}

@JsonClass(generateAdapter = true)
data class FunctionSpec(val name: String, val returnType: Type, val parameters: Map<String, Type>)
