Moshi-sealed
============

Reflective and code gen implementations for serializing Kotlin sealed classes via Moshi polymorphic adapters.

Simply annotate a sealed class with `@JsonClass` with a `generator` value of `sealed:{typeLabel}`.
 `{typeLabel}` is the value of the type label that should be used for a Moshi 
 `PolymorphicJsonAdapterFactory`. Annotate subtypes with `@TypeLabel` to indicate their type label 
 value. One `object` can be used as an unknown default value via `@DefaultObject`, or the sealed 
 type can be annotated with `@DefaultNull` to indicate that `null` should be the default.

```kotlin
@JsonClass(generateAdapter = true, generator = "sealed:type")
sealed class Message {

  @TypeLabel("success")
  @JsonClass(generateAdapter = true)
  data class Success(val value: String) : Message()

  @TypeLabel("error")
  @JsonClass(generateAdapter = true)
  data class Error(val error_logs: Map<String, Any>) : Message()

  @DefaultObject
  object Unknown : Message()
}
```

#### `object` subtypes

`object` types are useful in cases when receiving empty JSON objects (`{}`) or cases where its
type can be inferred by some delegating adapter that peeks its keys. They should only be used for
types that are indicator types and do not actually contain meaningful other data.

In the below example, we have a `FunctionSpec` that defines the signature of a function and a
`Type` representations that can be used to model its return type and parameter types. These are all
`object` types, so any contents are skipped in its serialization and only its `type` key is read
by the `PolymorphicJsonAdapterFactory` to determine its type.

```kotlin
@JsonClass(generateAdapter = true, generator = "sealed:type")
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

// Usage
val json = """
 {
   "name": "tacoFactory",
   "returnType": { "type": "void" },
   "parameters": {
     "param1": { "type": "int" },
     "param2": { "type": "boolean" }
   }
 }
""".trimIndent()

val functionSpec = moshi.adapter<FunctionSpec>().fromJson(json)
assertThat(functionSpec).isEqualTo(FunctionSpec(
        name = "tacoFactory",
        returnType = VoidType,
        parameters = mapOf("param1" to IntType, "param2" to BooleanType)
))
```

### Nested sealed types

In some cases, it's useful to have more than one level of sealed types that share the same label key.

```kotlin
sealed interface Response {
  data class Success(val value: String) : Response
  sealed interface Failure : Response {
   data class ErrorMap(val errors: List<String>) : Failure
   data class ErrorString(val error: String) : Failure
  }
}
```

moshi-sealed supports this out of the box via `@NestedSealed` annotation. Simply indicate the nested type with this
annotation.

```kotlin
@JsonClass(generateAdapter = true, generator = "sealed:type")
sealed interface Response {
  @TypeLabel("success")
  @JsonClass(generateAdapter = true)
  data class Success(val value: String) : Response
 
  @NestedSealed
  sealed interface Failure : Response {
    @TypeLabel("error_map")
    @JsonClass(generateAdapter = true)
    data class ErrorMap(val errors: List<String>) : Failure

    @TypeLabel("error_string")
    @JsonClass(generateAdapter = true)
    data class ErrorString(val error: String) : Failure
  }
}
```

In this case, now `Failure`'s subtypes will also participate in `Response` decoding based on the `type` label key.

Caveats:
* `@DefaultObject` is only supported on direct subtypes.
* `object` subtypes are currently only supported on direct subtypes.
* If you want to look up a subtype rather than the root parent sealed type (i.e. `moshi.adapter<Response.Failure>()`),
  you must add the optional `NestedSealed.Factory` `JsonAdapter.Factory` to your `Moshi` instance for runtime lookup.
  ```kotlin
  val moshi = Moshi.Builder()
    .add(NestedSealed.Factory())
    .build()
  ```

#### `@FallbackJsonAdapter`

In some cases, you may want to use a fallback `JsonAdapter` for a sealed type. To do this, you can annotate a sealed 
type with `@FallbackJsonAdapter` and specify a custom `JsonAdapter` class for it to use. This adapter will be created at
runtime and provided to the underlying Moshi `PolymorphicJsonAdapterFactory.withFallbackJsonAdapter(...)`.

**Note**: This will only cover cases of unrecognized type labels. Missing type labels are [not supported currently by Moshi](https://github.com/square/moshi/issues/1512).

This should usually only be reserved for advanced usage and is not recommended for most cases. You can only use one of
`@DefaultObject`, `@DefaultNull`, or `@FallbackJsonAdapter` on any given sealed type.

### Installation

Moshi-sealed can be used via reflection or code generation. Note that you must include the 
`moshi-adapters` artifact as a dependency, as that's where the `PolymorphicJsonAdapter` implementation
lives.

`@TypeLabel` and default indicator annotations are available in the `moshi-sealed-runtime` artifact.

[![Maven Central](https://img.shields.io/maven-central/v/dev.zacsweers.moshix/moshi-sealed-runtime.svg)](https://mvnrepository.com/artifact/dev.zacsweers.moshix/moshi-sealed-runtime)
```gradle
implementation "dev.zacsweers.moshix:moshi-sealed-runtime:{version}"
```

#### Code gen

Code gen works via [KSP](https://github.com/google/ksp) and only requires adding the KSP configuration dependency:

[![Maven Central](https://img.shields.io/maven-central/v/dev.zacsweers.moshix/moshi-sealed-codegen.svg)](https://mvnrepository.com/artifact/dev.zacsweers.moshix/moshi-sealed-codegen)
```kotlin
dependencies {
  ksp("dev.zacsweers.moshix:moshi-sealed-codegen:<version>")
}
```

No runtime Moshi instance configuration is needed, code gen will generate `JsonAdapter`s in a way that Moshi understands
natively.

To add `@Generated` controls, add them via ksp arguments

```kotlin
ksp {
  arg("moshi.generated", "javax.annotation.Generated")
}
```

#### Reflection

Reflection works via `MoshiSealedJsonAdapterFactory`. Just add this in moshi before 
`KotlinJsonAdapterFactory` in your Moshi instance construction.

```kotlin
val moshi = Moshi.Builder()
    .add(MoshiSealedJsonAdapterFactory())
    .add(KotlinJsonAdapterFactory())
    .build()
```

Gradle dependency:

[![Maven Central](https://img.shields.io/maven-central/v/dev.zacsweers.moshix/moshi-sealed-reflect.svg)](https://mvnrepository.com/artifact/dev.zacsweers.moshix/moshi-sealed-reflect)
```gradle
implementation "dev.zacsweers.moshix:moshi-sealed-reflect:{version}"
```

#### kotlinx-metadata based reflection

You can also use the kotlinx-metadata based version of reflection artifact, which cuts off the cost
of including kotlin-reflect. In your moshi instance construction, use
`MetadataMoshiSealedJsonAdapterFactory`:

```kotlin
val moshi = Moshi.Builder()
    .add(MetadataMoshiSealedJsonAdapterFactory())
    .add(KotlinJsonAdapterFactory())
    .build()
```

Gradle dependency:

[![Maven Central](https://img.shields.io/maven-central/v/dev.zacsweers.moshix/moshi-sealed-metadata-reflect.svg)](https://mvnrepository.com/artifact/dev.zacsweers.moshix/moshi-sealed-metadata-reflect)
```gradle
implementation "dev.zacsweers.moshix:moshi-sealed-metadata-reflect:{version}"
```

#### Java `sealed` classes support

Experimental support for Java `sealed` classes and interfaces in moshi-sealed via new
`moshi-sealed-java-sealed-reflect` artifact.

See `JavaSealedJsonAdapterFactory`.  Requires JDK 16 + `--enable-preview`.

```java
Moshi moshi = new Moshi.Builder()
    .add(new JavaSealedJsonAdapterFactory())
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

Gradle dependency:

[![Maven Central](https://img.shields.io/maven-central/v/dev.zacsweers.moshix/moshi-sealed-java-sealed-reflect.svg)](https://mvnrepository.com/artifact/dev.zacsweers.moshix/moshi-sealed-java-sealed-reflect)
```gradle
implementation "dev.zacsweers.moshix:moshi-sealed-java-sealed-reflect:{version}"
```

Snapshots of the development version are available in [Sonatype's snapshots repository][snapshots].

License
-------

    Copyright (C) 2020 Zac Sweers

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

 [snapshots]: https://oss.sonatype.org/content/repositories/snapshots/dev/zacsweers/moshix/
