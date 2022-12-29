Changelog
=========

Version 0.21.0
--------------

_2022-12-28_

#### **New:** Update to Kotlin `1.8.0`.
- For moshi-ir, this release is only compatible with Kotlin 1.8 or later.
- Migrate the IR and FIR plugins to new `CompilerPluginRegistrar` entrypoint API.

#### **New:** Experimental support for the new K2 compiler in moshi-ir.

Note this comes with several caveats:
- There is no FIR plugin in moshi-ir itself (not needed). This just marks itself as compatible with the new compiler and avoids using incompatible IR APIs.
- This only works if proguard rule generation is disabled, as there is no support in FIR currently for generating files.
- K2 compiler itself is extremely experimental.

In short, this is only really to unblock anyone doing their own testing of K2 and don't want this
plugin to disable it. If you see any issues, please file a bug here and disable K2 in your project
in the meantime.

#### Misc
- Update JVM target to `11`.
- Update Anvil `compiler-utils` to `2.4.3`.

Version 0.20.0
--------------

_2022-12-04_

#### **New:** `@FallbackJsonAdapter`

moshi-sealed now supports a new `@FallbackJsonAdapter`. This is a proxy to Moshi's `PolymorphicJsonAdapter.withFallbackJsonAdapter()`. This allows you to specify a custom `JsonAdapter` to handle cases of unrecognized type labels in decoding. It's advanced usage and not recommended for regular cases.

The specified `JsonAdapter` must have a public constructor with no parameters or a single `Moshi` parameter.

```kotlin
@FallbackJsonAdapter(FrogFallbackJsonAdapter::class)
@JsonClass(generateAdapter = true, generator = "sealed:type")
sealed class Frog {

  @JsonClass(generateAdapter = true)
  @TypeLabel("original", null)
  data class OriginalFrog(...)

  @JsonClass(generateAdapter = true)
  @TypeLabel("poisonous")
  data class PoisonousFrog(...)

  class FrogFallbackJsonAdapter(moshi: Moshi) : JsonAdapter<Frog>() {
    private val delegate = moshi.adapter<OriginalFrog>()
    override fun fromJson(reader: JsonReader): Frog? {
      // Default to original frog
      return delegate.fromJson(reader)
    }

    //...
  }
}
```

#### ~**New:** Experimental support for the new K2 compiler in moshi-ir.~

**Edit:** this isn't usable in this release, don't try to use it!

#### Misc

* **Enhancement:** Generate extra proguard rules for `@NestedSealed` types to prevent R8 from inlining the parent sealed type into the subtype in some cases.
* **Enhancement:** Proguard generation in moshi-ir now uses [Anvil](https://github.com/square/anvil)'s more optimized compiler util APIs.
* **Enhancement:** Check and report more error cases in moshi-ir and moshi-sealed code gen.
* **Fix:** moshi-ir now properly generates proguard rules for moshi-sealed types.
* **Fix:** track `@NestedSealed` types as originating files in moshi-sealed KSP.
* **Enhancement:** Substantially improved KSP and IR test coverage of error cases.
* Update to Kotlin `1.7.22`.
* Update to KSP `1.7.22-1.0.8`.

Version 0.19.0
--------------

_2022-09-29_

* Update to Kotlin `1.7.20`.
* Update to KSP `1.7.20-1.0.6`.

Note this release requires Kotlin 1.7.20 or newer.

Version 0.18.3
--------------

_2022-07-01_

* **Fix:** Support `@Json.ignore` in `MetadataKotlinJsonAdapterFactory`.

Version 0.18.2
--------------

_2022-06-29_

* **Fix:** Incremental processing when sealed types are spread across multiple files now works
  correctly for KSP code gen in moshi-sealed. Thanks to [@efemoney](https://github.com/efemoney).
* Update KotlinPoet to 1.12.0.
* Update kotlinx-metadata to 0.5.0.

Version 0.18.1
--------------

_2022-06-11_

Add a missing proguard rule for `@AdaptedBy` annotations to ensure they're kept on classes that use
them. Unfortunately there doesn't appear to be a more granular way preserve these annotations without
also keeping the whole class.

Version 0.18.0
--------------

_2022-06-10_

* Update to Kotlin 1.7.0 (Kotlin 1.6.x is no longer supported).
* Remove remaining use of deprecated descriptor APIs in IR.
* Update to KSP 1.7.0-1.0.6

Version 0.17.2
--------------

_2022-05-27_

**Fix:** Fix IR lookups of `setOf()` overloads. There are two `setOf()` functions with one arg - one
is the vararg and the other is a shorthand for `Collections.singleton(element)`. It's important we
pick the right one, otherwise we can accidentally send a vararg array into the `singleton()`
function.

Dependency updates:
```
Kotlin 1.6.21
kotlinpoet 1.11.0
kotlinx-metadata 0.4.2
```

Version 0.17.1
--------------

_2022-03-09_

**Fix:** Fix support for nested sealed types that don't use `@JsonClass`.

Version 0.17.0
--------------

_2022-02-16_

#### **New:** moshi-sealed now supports _nested_ sealed subtypes!

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

moshi-sealed now supports this out of the box via `@NestedSealed` annotation. Simply indicate the nested type with this
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
* If you want to look up a subtype rather than the root parent sealed type (i.e. `moshi.adapter<Response.Failure>()`),
  you must add the optional `NestedSealed.Factory` `JsonAdapter.Factory` to your `Moshi` instance for runtime lookup.
  ```kotlin
  val moshi = Moshi.Builder()
    .add(NestedSealed.Factory())
    .build()
  ```

#### Kapt is no longer supported by moshi-sealed

`moshi-sealed` has many implementations - `kotlin-reflect`, `kotlinx-metadata`, KSP, Java sealed classes, and
recently IR. These are a lot to maintain! To cut down on maintenance, Kapt is no longer supported and has been removed
in this release. Please consider migrating to KSP or Moshi-IR.

#### **Fix:** Properly report all originating files in KSP

With Kotlin 1.5.0, sealed types could now exist across multiple files. `moshi-sealed`'s KSP support previously assumed
single files when reporting originating elements, and now properly reports all files if sealed types are spread
across multiple files.

Version 0.16.7
--------------

_2022-02-01_

**moshi-ir**

* **Fix:** Use `FilesSubpluginOption` to fix build cache relocatability when generating proguard rules.

Version 0.16.6
--------------

_2022-01-27_

**moshi-ir**

* **Fix:** Nested type argument use in properties would fail in 0.16.5's new type rendering. This is now fixed.
  Example failing case would've been something like this:
  ```kotlin
  @JsonClass(generateAdapter = true)
  class Foo<T>(val value: List<T>)
  ```

Version 0.16.5
--------------

_2022-01-20_

* **Enhancement:** Generate manual `Type` construction in `moshi-ir` adapter lookups. Prior to this, we generated IR
  code that leveraged `typeOf()`, but this appears to be too late to leverage compiler intrinsics support for it and
  appears to cause some issues if `kotlin-reflect` is on the classpath. This should improve runtime performance as a
  result.

Version 0.16.4
--------------

_2022-01-13_

* **Fix:** Add moshi/moshi-sealed-runtime dependencies as `implementation` rather than `api` when applying the
  `moshi-ir` plugin. [#200](https://github.com/ZacSweers/MoshiX/pull/200)
* **Fix:** A second attempt at fixing extension point issues with AnalysisHandlerExtension in `moshi-ir`'s proguard
  rule generation. [#201](https://github.com/ZacSweers/MoshiX/pull/201)

Thanks to [@gpeal](https://github.com/gpeal) for contributing to this release!

Version 0.16.3
--------------

_2022-01-11_

* **Fix:** Build new type parameters when generating classes in `moshi-ir` rather than incorrectly reuse the
  original class's parameters. Resolves [this issue](https://issuetracker.google.com/issues/213578515) (that was
  originally believed to be a Compose issue).

Version 0.16.2
--------------

_2022-01-06_

* **Fix:** Pass `generateProguardRules` Gradle plugin option correctly.
* **Fix:** Best-effort avoid synchronization race with IntelliJ openapi when registering proguard rule gen extension

Version 0.16.1
--------------

_2022-01-06_

* `moshi-ir` now supports dynamic generation of proguard rules, bringing it to feature parity with Moshi's existing
  code gen.
  * Note that if you have any issues, this can be disabled via the Gradle extension's `generateProguardRules`
    property and using the manual rules mentioned in version 0.16.0's notes.
    ```gradle
    moshi {
      generateProguardRules.set(false)
    }
    ```
* **New:** To help with debugging `moshi-ir`, a new `debug` property is available in the Gradle extension. It is off
  by default and can be enabled like below. Please try this out and include its output when reporting issues. Thanks!
  ```gradle
  moshi {
    debug.set(true)
  }
  ```

Version 0.16.0
--------------

_2021-12-24_

#### **New:** [moshi-ir](https://github.com/ZacSweers/MoshiX/tree/main/moshi-ir)

An experimental Kotlin IR implementation of Moshi code gen and moshi-sealed code gen.

The goal of this is to have functional parity with their native Kapt/KSP code gen analogues but run as a fully
embedded IR plugin.

**Benefits**
- Significantly faster build times.
  - No extra Kapt or KSP tasks, no extra source files to compile. This runs directly in kotlinc and generates IR that is
    lowered directly into bytecode.
- No reflection required at runtime to support default parameter values.
- Feature parity with Moshi's native code gen.
- More detailed error messages for unexpected null values and missing properties. Now all errors are accumulated and
  reported at the end, rather than failing eagerly with just the first one encountered.
  - See https://github.com/square/moshi/issues/836 for more details!

**Cons**
- No support for Proguard file generation for now [#193](https://github.com/ZacSweers/MoshiX/issues/193). You will
  need to add this manually to your rules if you use R8/Proguard.
  - One option is to use IR in debug builds and Kapt/KSP in release builds, the latter of which do still generate
    proguard rules.
  ```proguard
  # Keep names for JsonClass-annotated classes
  -keepnames @com.squareup.moshi.JsonClass class **

  # Keep generated adapter classes' constructors
  -keepclassmembers class *JsonAdapter {
      public <init>(...);
  }
  ```
- Kotlin IR is not a stable API and may change in future Kotlin versions. While I'll try to publish quickly to adjust to
  these, you should be aware. If you have any issues, you can always fall back to Kapt/KSP.

### Installation

Simply apply the Gradle plugin in your project to use it. You can enable moshi-sealed code gen via the `moshi`
extension.

The Gradle plugin is published to Maven Central, so ensure you have `mavenCentral()` visible to your buildscript
classpath.

[![Maven Central](https://img.shields.io/maven-central/v/dev.zacsweers.moshix/moshi-gradle-plugin.svg)](https://mvnrepository.com/artifact/dev.zacsweers.moshix/moshi-gradle-plugin)
```gradle
plugins {
  kotlin("jvm")
  id("dev.zacsweers.moshix") version "x.y.z"
}

moshi {
  // Opt-in to enable moshi-sealed, disabled by default.
  enableSealed.set(true)
}
```

#### Other

- Update to Kotlin `1.6.10`
- Update to KSP `1.6.10-1.0.2`

Version 0.15.0
--------------

_2021-12-10_

* Update to Moshi `1.13.0`
* **Removed:** The `moshi-ksp` artifact has been upstreamed to Moshi itself as is no longer published.
* **Removed:** The `moshi-records-reflect` artifact has been upstreamed to Moshi itself as is no longer published.
* Update to Kotlin `1.6.0`
* Update to KotlinPoet `1.10.2`

Version 0.14.1
--------------

_2021-09-21_

* Build against JDK 17.
  * This means that `moshi-sealed-java-sealed-reflect`'s support of `sealed` classes in Java is now out of preview
    and requires Java 17 to use.
  * `moshi-records-reflect` still targets Java 16 for maximum compatibility.
  * All other artifacts still target Java 8.
* Update Kotlin to `1.5.31`
* Update KotlinPoet to `1.10.1`

Version 0.14.0
--------------

_2021-09-07_

* Update KSP to `1.5.30-1.0.0` stable!
* `moshi-sealed-ksp` has now been merged into `moshi-sealed-codegen`. This artifact can be used for both `kapt` and
  `ksp`.
* `moshi-ksp` is now _soft-deprecated_ and will be fully deprecated once Moshi's next release is out with formal support.

Version 0.13.0
--------------

_2021-08-27_

* Update Kotlin to `1.5.30`.
* Update KSP to `1.5.30-1.0.0-beta08`.
* **Enhancement:** `RecordsJsonAdapterFactory` is now aligned with the upstreamed implementation on Moshi itself.
  * Note that this is now _soft-deprecated_ and will be fully deprecated once Moshi's next release is out with formal support.
  * This includes using a few more modern language APIs like `MethodHandle` and better unpacking of different runtime exceptions. Full details can be found in the [PR](https://github.com/square/moshi/pull/1381).
* **Fix:** Avoid implicitly converting elements to KotlinPoet in `CodeBlock`s to avoid noisy logging.
* **Fix:** Improve self-referencing type variables parsing in `moshi-ksp` (see [#125](https://github.com/ZacSweers/MoshiX/pull/125) and [#151](https://github.com/ZacSweers/MoshiX/pull/151)).

Special thanks to [@yigit](https://github.com/yigit) for contributing to this release!

Version 0.12.2
--------------

_2021-08-20_

* **Fix:** `RecordsJsonAdapterFactory` now properly respects `@JsonQualifier` annotations on components.
* **Fix:** `RecordsJsonAdapterFactory` now supports non-public constructors (i.e. package or file-private).
* **Fix:** Crash in `moshi-ksp` when dealing with generic typealias properties.

Version 0.12.1
--------------

_2021-08-19_

* Update to KSP `1.5.21-1.0.0-beta07`.
* **Fix:** Previously if you had a `@JsonClass`-annotated Java file with a custom generator, `moshi-ksp` would error
  out anyway due to it not being a Kotlin class. This is now fixed and it will safely ignore these files.
* **Fix:** Generate missing `@OptIn(ExperimentalStdLibApi::class)` annotations in `moshi-sealed` when `object`
  adapters are used, as we use Moshi's reified `addAdapter` extension.

Thanks to [@gabrielittner](https://github.com/gabrielittner) for contributing to this release!

Version 0.12.0
--------------

_2021-07-15_

* Update to KSP `1.5.21-1.0.0-beta05`.
* Update to Kotlin `1.5.21`.
* Update to Dokka `1.5.0`.
* Update to KotlinPoet `1.9.0`.
* Test against JDK 17 early access previews.
* **New:** `moshi-ksp` and moshi-sealed's codegen both support a new `moshi.generateProguardRules` option. This can be
  set to `false` to disable proguard rule generation.
* **Fix:** Artifacts now ship with a `-module-name` attribute set to their artifact ID to help avoid module name
  collisions.

Thanks to [@SeongUgJung](https://github.com/SeongUgJung) and [@slmlt](https://github.com/slmlt) for contributing to this
release!

Version 0.11.2
--------------

_2021-05-31_

* `moshi-ksp` - Fix a bug where supertypes compiled outside the current compilation weren't recognized as Kotlin types.

Version 0.11.1
--------------

_2021-05-27_

* Update to KSP `1.5.10-1.0.0-beta01`
* Update to Kotlin `1.5.10`

Version 0.11.0
--------------

_2021-05-14_

#### Project-wide

* Update Kotlin to `1.5.0`.
* Update deprecated Kotlin stdlib usages during `1.5.0` upgrade.
* Support Java 16.
* Update KotlinPoet to `1.8.0`.
* Small documentation improvements.

#### All KSP artifacts

* Update KSP to `1.5.0-1.0.0-alpha10`.
* Switch to new `SymbolProcessorProvider` APIs.
* Adopt new `Sequence`-based KSP APIs where possible.

#### All metadata-reflect artifacts

* Update kotlinx-metadata to `0.3.0`.

#### moshi-ksp

* **Fix:** Don't fail on annotations that are `typealias`'d.
* **Fix:** Support enum entry values in copied `@JsonQualifier` annotations.
* **Fix:** Support array values in copied `@JsonQualifier` annotations.

#### moshi-sealed

* **Enhancement:** sealed interfaces and package-wide sealed classes are fully supported in KSP, kapt, reflect, and
  metadata-reflect.
* **Fix:** Make `moshi-adapters` an `api` dependency in `moshi-sealed-runtime`

#### moshi-records-reflect

* `RecordsJsonAdapterFactory` is no longer in preview and now built against JDK 16.
* **New:** A dedicated README page can be found [here](https://github.com/ZacSweers/MoshiX/tree/main/moshi-records-reflect).

```java
final record Message(String value) {
}

public static void main(String[] args) {
  Moshi moshi = new Moshi.Builder()
    .add(new RecordsJsonAdapterFactory())
    .build();

  JsonAdapter<Message> messageAdapter = moshi.adapter(Message.class);
}
```

#### moshi-sealed: java-sealed-reflect

* `JavaSealedJsonAdapterFactory` is now built against JDK 16. Note this feature is still in preview.
* **New:** A dedicated README section can be found [here](https://github.com/ZacSweers/MoshiX/tree/main/moshi-sealed#java-sealed-classes-support).

_Thanks to the following contributors for contributing to this release! [@remcomokveld](https://github.com/remcomokveld), [@martinbonnin](https://github.com/martinbonnin), and [@eneim](https://github.com/eneim)_

Version 0.10.0
-------------

_2021-04-09_

* Update KSP to `1.4.32-1.0.0-alpha07`.
* `moshi-ksp` - Report missing primary constructor JVM signatures to `KSPLogger`.
* Update Kotlin to `1.4.32`.
* Update Moshi to `1.12.0`.

Version 0.9.2
-------------

_2021-03-01_

#### KSP

* Update KSP to `1.4.30-1.0.0-alpha04` in KSP-using libraries. Among other changes, these processors now run all
  errors through KSP's native `KSPLogger.error()` API now.

#### moshi-ksp

* **Fix:** Support function types as property types.
* **Fix:** Support generic arrays when invoking defaults constructors.
* Some small readability improvements to generated code.

#### Moshi-sealed

* Add tests for Kotlin 1.4.30's preview support for sealed interfaces. These won't be officially supported until
  Kotlin 1.5, but they do appear to Just Work™️ since Kotlin reuses the same sealed APIs under the hood.
* Support Kotlin 1.5's upcoming sealed interfaces in KSP.

Version 0.9.1
-------------

_2021-02-15_

* Update to Kotlin `1.4.30`.

#### KSP

_Applies to all KSP-using artifacts._

* Update to KSP `1.4.30-1.0.0-alpha02`. Note that `incremental` is now _on_ by default.

#### moshi-ksp

* **Fix:** Reserve property type simple names eagerly to avoid collisions like https://github.com/square/moshi/issues/1277
* **Fix:** Include `"RedundantVisibilityModifier"` suppression in generated adapters to cover for KotlinPoet's
  explicit `public` modifiers.
* **Enhancement:** Invoke constructor directly in generated adapters if all parameters with defaults are present in
  the JSON. This allows generated adapters to avoid reflective lookup+invocation of the Kotlin synthetic defaults
  constructor that we otherwise have to use to support default parameter values.

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

* **New:** `@TrackUnknownKeys` annotation + factory to record unknown keys in a JSON body. See its doc for more
  information. This API should be treated as experimental (even by MoshiX standards), feedback welcome on how best
  to improve the API!

  ```kotlin
  val moshi = Moshi.Builder()
    .add(TrackUnknownKeys.Factory())
    .build()

  @TrackUnknownKeys
  @JsonClass(generateAdapter = true)
  data class Message(
    val data: String
  )

  // JSON of {"data": "value", "foo": "bar"} would report an unknown "foo"
  ```

#### moshi-metadata-reflect

* **Fix:** Embedded proguard rules now keep the right package for kotlinx-metadata extensions.

_Special thanks to [@efemoney](https://github.com/efemoney) and [@plnice](https://github.com/plnice) for
contributing to this release!_

Version 0.9.0
-------------

This version had a bug in releasing, please ignore.

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
