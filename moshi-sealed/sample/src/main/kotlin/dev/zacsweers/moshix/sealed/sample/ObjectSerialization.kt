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
@JsonClass(generateAdapter = false, generator = "sealed:type")
sealed class Type(val type: String) {
  @TypeLabel("void")
  object VoidType : Type("void")
  @TypeLabel("boolean")
  object BooleanType : Type("boolean")
  @TypeLabel("int")
  object IntType : Type("int")

  override fun toString() = type
}

data class FunctionSpec(
  val name: String,
  val returnType: Type,
  val parameters: Map<String, Type>
)
