Changelog
=========

NEXT
-------------------

_WIP_

* Update to Kotlin `1.4.30`.

#### KSP

_Applies to all KSP-using artifacts._

* Update to KSP `1.4.30-1.0.0-alpha02`. Note that `incremental` is now _on_ by default.

#### moshi-ksp

* **Fix:** Reserve property type simple names eagerly to avoid collisions like https://github.com/square/moshi/issues/1277
* **Fix:** Generate `@Suppress` annotations with suppressions for common warnings in generated code

#### moshi-sealed

_Changes apply to all moshi-sealed implementations (Java, reflect, KSP, code gen, etc) unless otherwise 
specified._

* **New:** `moshi-sealed-metadata-reflect` artifact with a `kotlinx-metadata`-based implementation, allowing 
  reflective use without `kotlin-reflect`.

  [![Maven Central](https://img.shields.io/maven-central/v/dev.zacsweers.moshix/moshi-sealed-metadata-reflect.svg)](https://mvnrepository.com/artifact/dev.zacsweers.moshix/moshi-sealed-metadata-reflect)
  ```gradle
  implementation "dev.zacsweers.moshix:moshi-sealed-metadata-reflect:{version}"
  ```

* **Fix:** Check for generic sealed subtypes. The base sealed type can be generic, but subtypes cannot since we 
  can't plumb their generic information down to them when looking up from the base alone!
* **Fix:** Code gen and ksp now respect `JsonClass.generateAdapter`.
* **Fix:** KSP failing to find sealed subclasses when sealed base class is generic.
* **Fix:** Check for duplicate labels.
* **Fix:** KSP now routes all errors through `KSPLogger.error()`.
* **Fix:** Generate `@Suppress` annotations with suppressions for common warnings in generated code in both KSP and 
  code gen.

#### moshi-adapters

* **New:** `@JsonString` can now be used on functions/methods, allowing use in more scenarios like AutoValue and 
  Retrofit.
  
  ```kotlin
  interface TacoApi {
    @JsonString
    @GET("/")
    fun getTacosAsRawJsonString(): String
  }
  ```

#### moshi-metadata-reflect

* **Fix:** Embedded proguard rules now keep the right package for kotlinx-metadata extensions.

_Special thanks to [@efemoney](https://github.com/efemoney) and [@plnice](https://github.com/plnice) for 
contributing to this release!_

Version 0.8.0
-------------------

_2021-01-27_

* **New:** Experimental support for Java `record` classes via new `moshi-records-reflect` artifact. See 
`RecordsJsonAdapterFactory`. Requires JDK 15 + `--enable-preview`.
  ```java
  Moshi moshi = new Moshi.Builder()
      .add(new RecordsJsonAdapterFactory())
      .build();
  
  final record Message(String value) {
  }
  ```

* **New:** Experimental support for Java `sealed` classes and interfaces in moshi-sealed via new 
  `moshi-sealed-java-sealed-reflect` artifact. See `JavaSealedJsonAdapterFactory`.  Requires JDK 15 + `--enable-preview`.
  ```java
  Moshi moshi = new Moshi.Builder()
      .add(new JavaSealedJsonAdapterFactory())
      .add(new RecordsJsonAdapterFactory())
      .build();
  
  @JsonClass(generateAdapter = true, generator = "sealed:type")
  sealed interface MessageInterface
      permits MessageInterface.Success, MessageInterface.Error {
  
    @TypeLabel(label = "success", alternateLabels = {"successful"})
    final record Success(String value) implements MessageInterface {
    }
    
    @TypeLabel(label = "error")
    final record Error(Map<String, Object> error_logs) implements MessageInterface {
    }
  }
  ```

* **New:** `@AdaptedBy` annotation support in `moshi-adapters`. This is analogous to Gson's `@JsonAdapter` annotation,
 allowing you to annotate a class or a property with it to indicate which `JsonAdapter` or `JsonAdapter.Factory`
 should be used to encode it.
 ```Kotlin
  val moshi = Moshi.Builder()
    .add(AdaptedBy.Factory())
    .build()
  
  @AdaptedBy(StringAliasAdapter::class)
  data class StringAlias(val value: String)
  
  class StringAliasAdapter : JsonAdapter<StringAlias>() {
    override fun fromJson(reader: JsonReader): StringAlias? {
      return StringAlias(reader.nextString())
    }
  
    override fun toJson(writer: JsonWriter, value: StringAlias?) {
      if (value == null) {
        writer.nullValue()
        return
      }
      writer.value(value.value)
    }
  }
  ```

Version 0.7.1
-------------

_2021-01-11_

* Update to KSP `1.4.20-dev-experimental-20210111`.

Version 0.7.0
-------------

_2020-12-26_

This introduces support for KSP's new incremental processing support. Because all outputs in both
`moshi-ksp` and `moshi-sealed`'s `codegen-ksp`, both of them are effectively "isolating" processors.

Note that incremental processing itself is _not_ enabled by default and must be enabled via
`ksp.incremental=true` Gradle property. See KSP's release notes for more details:
https://github.com/google/ksp/releases/tag/1.4.20-dev-experimental-20201222

* KSP `1.4.20-dev-experimental-20201222`
* Kotlin `1.4.20`

Version 0.6.1
-------------

_2020-11-12_

`moshi-ksp` and `moshi-sealed-ksp` are now built against KSP version `1.4.10-dev-experimental-20201110`.

Version 0.6.0
-------------

_2020-10-30_

#### moshi-sealed

`@TypeLabel` now has an optional `alternateLabels` array property for cases where multiple labels 
can match the same sealed subtype.

```kotlin
@JsonClass(generateAdapter = true, generator = "sealed:type")
sealed class Message {

  @TypeLabel("success", alternateLabels = ["successful"])
  @JsonClass(generateAdapter = true)
  data class Success(val value: String) : Message()
}
```

**NOTE:** We also changed `@TypeLabel`'s `value` property to the more meaningful `label` name. This 
is technically a breaking change, but should be pretty low impact since most people wouldn't be 
defining this parameter name or reading the property directly.

Version 0.5.0
-------------

_2020-10-25_

Dependency updates for all code generation artifacts:
* KSP `1.4.10-dev-experimental-20201023`
* KotlinPoet `1.7.2`

#### moshi-ksp

* Use KSP's new `asMemberOf` API for materializing type parameters, allowing us to remove a lot of ugly
  `moshi-ksp` code that existed to accomplish the same.
* Defer failing the compilation when errors are reported to the `KSPLogger` until the end of the KSP run,
  allowing reporting all errors rather than just the first.

#### moshi-sealed

`moshi-sealed-codegen` and `moshi-sealed-codegen-ksp` now generate proguard rules for generated adapters
on the fly, matching Moshi's new behavior introduced in 1.10.0.

Thanks to [@plnice](https://github.com/plnice) for contributing to this release.

Version 0.4.0
-------------

_2020-10-12_

Updated Moshi to 1.11.0

#### moshi-ksp

Updated to `1.4.10-dev-experimental-20201009`

#### moshi-ktx

Removed! These APIs live in Moshi natively now as of 1.11.0

#### moshi-adapters

New artifact!

First adapter in this release is a new `@JsonString` qualifier + adapter, so you can 
capture raw JSON content from payloads. This is adapted from the recipe in Moshi.

```Kotlin
val moshi = Moshi.Builder()
  .add(JsonString.Factory())
  .build()

@JsonClass(generateAdapter = true)
data class Message(
  val type: String,
  /** Raw JSON string for the `data` key. */
  @JsonString val data: String
)
```

Get it via

```kotlin
dependencies {
  implementation("dev.zacsweers.moshix:moshi-adapters:<version>")
}
```

#### moshi-sealed

New support for multiple `object` subtypes. This allows for sentinel types who only contain an indicator
label but no other data.

In the below example, we have a `FunctionSpec` that defines the signature of a function and a
`Type` representations that can be used to model its return type and parameter types. These are all
`object` types, so any contents are skipped in its serialization and only its `type` key is read
by the `PolymorphicJsonAdapterFactory` to determine its type.

```kotlin
@JsonClass(generateAdapter = false, generator = "sealed:type")
sealed class Type(val type: String) {
  @TypeLabel("void")
  object VoidType : Type("void")
  @TypeLabel("boolean")
  object BooleanType : Type("boolean")
  @TypeLabel("int")
  object IntType : Type("int")
}

data class FunctionSpec(
 val name: String,
 val returnType: Type,
 val parameters: Map<String, Type>
)
```

**NOTE**: As part of this change, the `moshi-sealed-annotations` artifact was replaced with a
`moshi-sealed-runtime` artifact. Please update your coordinates accordingly, and don't use `compileOnly`
anymore.

Version 0.3.2
-------------

_2020-10-01_

Fixes two issues with `moshi-ksp`:
- Handle `Any` superclasses when the supertype is from another module
- Filter out non-`CLASS` kinds from supertypes

Special thanks to [@JvmName](https://github.com/JvmName) for reporting and helping debug this!

Version 0.3.1
-------------

_2020-09-30_

`moshi-ksp` now fully supports nullable generic types, which means it is now at feature parity with
Moshi's annotation-processor-based code gen 🥳

Version 0.3.0
-------------

_2020-09-27_

### THIS IS BIG

This project is now **MoshiX** and contains multiple Moshi extensions.

* **New:** [moshi-ksp](https://github.com/ZacSweers/MoshiX/blob/main/moshi-ksp/README.md) - A [KSP](https://github.com/google/ksp) implementation of Moshi Kotlin Codegen.
* **New:** [moshi-ktx](https://github.com/ZacSweers/MoshiX/blob/main/moshi-ktx/README.md) - Kotlin extensions for Moshi with no kotlin-reflect requirements and fully compatible with generic reified types via the stdlib's `typeOf()` API.
* **New:** [moshi-metadata-reflect](https://github.com/ZacSweers/MoshiX/blob/main/moshi-metadata-reflect/README.md) - A [kotlinx-metadata](https://github.com/JetBrains/kotlin/tree/master/libraries/kotlinx-metadata/jvm) based implementation of `KotlinJsonAdapterFactory`. This allows for reflective Moshi serialization on Kotlin classes without the cost of including kotlin-reflect.
* **Updated:** [moshi-sealed](https://github.com/ZacSweers/MoshiX/blob/main/moshi-sealed/README.md) - Largely unchanged, but now there is a new `moshi-sealed-ksp` artifact available for KSP users.

Some of these will eventually move to Moshi directly. This project going forward is a focused set of extensions that 
either don't belong in Moshi directly or can be a non-API-stable testing ground for early adopters.

Version 0.2.0
-------------

_2020-04-26_

* Fix reflect artifact depending on a snapshot Moshi version.
* Update to Kotlin 1.3.72
* Update to KotlinPoet 1.5.0

Version 0.1.0
-------------

_2019-10-29_

Initial release!
