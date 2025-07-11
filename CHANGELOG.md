Changelog
=========

**Unreleased**
--------------

0.31.0
------

_2025-06-28_

- Update to Kotlin `2.2.0`.
- Update to KSP `2.2.0-2.0.2`.
- Update gradle plugin to target Kotlin `1.9`.

0.30.0
------

_2025-03-26_

- Remove most IR internal/impl API usages.
- Update to Kotlin `2.1.20`. Note that this release requires Kotlin `2.1.20` or later for moshi-ir due to changes in the IR API. It may work on older releases, but it's untested.
- Build against KSP `2.1.20-1.0.31`.
- Build against Gradle `8.13`.

0.29.0
------

_2024-11-29_

- **New**: Add option to disable auto-application of the Moshi dependency in the moshi-ir Gradle plugin. `moshi { applyMoshiDependency.set(false) }`.
- Update to Kotlin `2.1.0`. Note that this release requires Kotlin `2.1.0` or later for moshi-ir due to changes in the IR API. It may work on older releases, but it's untested.
- Update KotlinPoet to `2.0.0`.
- Update Guava to `33.3.1-jre`.
- Update kotlinx-collections-immutable to `0.3.8`.
- Build against KSP `2.1.0-1.0.29`.
- Build against Gradle `8.11.1`.

Special thanks to [@plnice](https://github.com/plnice) for contributing to this release!

0.28.0
------

_2024-08-22_

- Make moshi-ir Gradle plugin compatible with Gradle's incubating "Project Isolation" feature.
- Update to Kotlin `2.0.20`. Note that this release requires Kotlin `2.0.20` or later for moshi-ir due to changes in the IR API. It may work on older releases, but it's untested.
- Update Guava to `33.3.0-jre`.
- Build against KSP `2.0.20-1.0.24`.
- Build against Gradle `8.10`.

Special thanks to [@ansman](https://github.com/ansman) for contributing to this release!

0.27.2
------

_2024-06-28_

- [moshi-proguard-rule-gen] Fix proguard rule gen when using nested classes or packages with soft-keyword segments.
- [docs] Add immutable-adapters in `README.md`.
- Build against KSP to `2.0.0-1.0.22`.
- Build against Gradle `8.8`.

Special thanks to [@mhelder](https://github.com/mhelder) and [@beigirad](https://github.com/beigirad) for contributing to this release!

0.27.1
------

_2024-05-28_

- [moshi-sealed] Improve moshi-sealed KSP error messages.
- [moshi-ir] Fix fallback adapter support in IR code gen not recognizing Moshi parameters to primary constructors.
- [moshi-sealed and moshi-ir] Check for same subtypes before erroring on duplicate labels in moshi-sealed IR.
- [moshi-proguard-rule-gen] Fix proguard rule gen not capturing non-sealed subtypes.
- [moshi-proguard-rule-gen] Don't write empty proguard rule files if not rules were necessary.
- Update KotlinPoet to `1.17.0`.

0.27.0
------

_2024-05-22_

### Update to K2

This release updates to K2, aka Kotlin `2.0.0`. This also builds against KSP `2.0.0-1.0.21`.

- [moshi-metadata-reflect] Update to stable kotlin metadata API that ships in K2. The new transitive dependency is now `org.jetbrains.kotlin:kotlin-metadata-jvm:2.0.0`.
- [ksp] All KSP processors are tested against both KSP 1 and the in-beta KSP2.
- [moshi-ir] Support K2 IR API changes. We may explore building out FIR support for better IDE support in the future, but for now the entire implementation remains in an IR-only plugin.

0.26.0
------

_2024-05-12_

### **New**: Publish a new `moshi-immutable-adapters` artifact with support for [kotlinx.collections.immutable](https://github.com/Kotlin/kotlinx.collections.immutable).

Gradle dependency

```kotlin
dependencies {
  implementation("dev.zacsweers.moshix:moshi-immutable-adapters:<version>")
}
```

In code

```kotlin
val moshi = Moshi.Builder().add(ImmutableCollectionsJsonAdapterFactory()).build()
```

**Supported types**

- `ImmutableCollection`
- `ImmutableList`
- `ImmutableSet`
- `ImmutableMap`
- `PersistentCollection`
- `PersistentList`
- `PersistentSet`
- `PersistentMap`

### Misc
- Omit the stdlib from transitive dependencies on the compiler plugin and Gradle plugin artifacts. Both kotlinc and Gradle impose their own versions on the classpath.
- Update Moshi to `1.15.1`.
- Update kotlinx-metadata to `0.9.0`.
- Update KotlinPoet to `1.16.0`.
- Update Kotlin to `1.9.24`.
- Update KSP to `1.9.24-1.0.20`.
- Update Guava to `33.2.0-jre`.
- Compile against Gradle `8.7`.

0.25.1
------

_2023-11-27_

- Update to Kotlin `1.9.21`.
- Update to KSP `1.9.20-1.0.14`.
- Update to KotlinPoet `1.15.1`.

0.25.0
------

_2023-10-31_

- Update to Kotlin `1.9.20`. moshi-ir now requires Kotlin `1.9.20`.
- Update to KSP `1.9.20-1.0.13`.
- Update to Guava `32.1.3-jre`.
- Build against Gradle `8.4`.

0.24.3
------

_2023-09-06_

- **Fix**: Enable KSP proguard rule gen again in moshi-ir. We accidentally encountered a [bug](https://github.com/google/ksp/issues/1524) in KSP and work around it now.

0.24.2
------

_2023-09-03_

- **Fix**: Use correct platform-specific configuration for applying KSP dependency in moshi-ir.
- **Fix**: Don't enable moshi-ir/ksp on non-JVM/Android platforms until they're supported upstream.

0.24.1
------

_2023-09-02_

- **Fix**: Use correct name for each `KotlinCompilation`'s `implementation` configuration in KMP projects. Note that Moshi still only supports JVM/Android.
- Update to Kotlin 1.9.10.
- Update to KSP 1.9.10-1.0.13.
- Compile against Gradle 8.3.
- Remove shaded anvil-compiler-utils dependency from moshi-ir.

0.24.0
------

_2023-07-22_

**New: Move proguard rule generation to a standalone KSP processor.**

This is necessary in order to support both K2 and avoid incremental compilation issues in Kotlin 1.9.x.

For moshi-sealed KSP users, there should be no changes necessary.

For moshi-ir users, you must now apply the KSP gradle plugin as well as the moshix plugin. MoshiX's gradle plugin does _not_ directly declare a transitive dependency on the KSP plugin to avoid Gradle classloader conflicts.

```diff
plugins {
  // Other plugins
  id("dev.zacsweers.moshix") version "x.y.z"
+  id("com.google.devtools.ksp") version "x.y.z"
}
```

If you don't want this or don't need proguard rule generation, you can opt out by setting the `moshix.generateProguardRules` gradle property to `false`.

- Update KSP to `1.9.0-1.0.12`.
- Update KotlinPoet to `1.14.2`.
- Update to kotlinx-metadata `0.7.0`.
- Update to Guava `32.1.1-jre`.

0.24.0-RC2
----------

_2023-07-20_

- **Fix:** Write generated proguard rules to the correct resources path.

0.24.0-RC
---------

_2023-07-18_

**New: Move proguard rule generation to a standalone KSP processor.**

This is necessary in order to support both K2 and avoid incremental compilation issues in Kotlin 1.9.x.

For moshi-sealed KSP users, there should be no changes necessary.

For moshi-ir users, you must now apply the KSP gradle plugin as well as the moshix plugin. MoshiX's gradle plugin does _not_ directly declare a transitive dependency on the KSP plugin to avoid Gradle classloader conflicts.

```diff
plugins {
  // Other plugins
  id("dev.zacsweers.moshix") version "x.y.z"
+  id("com.google.devtools.ksp") version "x.y.z"
}
```

If you don't want this or don't need proguard rule generation, you can opt out by setting the `moshix.generateProguardRules` gradle property to `false`.

This first release is an RC release to ensure there are no issues with the new standalone processor. If you encounter any issues, please file them!

0.23.0
------

_2023-07-06_

- Update to Kotlin `1.9.0`. The moshi-ir plugin now requires `1.9.0`.
- Update `kotlinx-metadata` to `0.6.2`.
- Update shaded Anvil utils to `2.4.6`.
- Update Moshi to `1.15.0`.
- Update KSP to `1.9.0-1.0.11`.

0.22.1
------

_2023-04-16_

**moshi-sealed**
Keep signatures for typed annotated with `@NestedSealed`. This ensures that the annotation, itself isn't stripped from the use on the class.

This is done via this keep rule in moshi-sealed-runtime's embedded proguard rules, which should still allow strinking/optimization of the class itself.

```proguard
-keepnames @dev.zacsweers.moshix.sealed.annotations.NestedSealed class **
```

0.22.0
------

_2023-04-03_

- Update to Kotlin `1.8.20`. Kotlin 1.8.20 or later is required for `moshi-ir`.
- Update to KSP `1.8.20-1.0.10`.
- Update kotlinx-metadata-jvm to `0.6.0`.
- **Fix**: Don't use experimental-gated addAdapter with generated object adapters.

0.21.0
------

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

0.20.0
------

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

0.19.0
------

_2022-09-29_

* Update to Kotlin `1.7.20`.
* Update to KSP `1.7.20-1.0.6`.

Note this release requires Kotlin 1.7.20 or newer.

0.18.3
------

_2022-07-01_

* **Fix:** Support `@Json.ignore` in `MetadataKotlinJsonAdapterFactory`.

0.18.2
------

_2022-06-29_

* **Fix:** Incremental processing when sealed types are spread across multiple files now works
  correctly for KSP code gen in moshi-sealed. Thanks to [@efemoney](https://github.com/efemoney).
* Update KotlinPoet to 1.12.0.
* Update kotlinx-metadata to 0.5.0.

0.18.1
------

_2022-06-11_

Add a missing proguard rule for `@AdaptedBy` annotations to ensure they're kept on classes that use
them. Unfortunately there doesn't appear to be a more granular way preserve these annotations without
also keeping the whole class.

0.18.0
------

_2022-06-10_

* Update to Kotlin 1.7.0 (Kotlin 1.6.x is no longer supported).
* Remove remaining use of deprecated descriptor APIs in IR.
* Update to KSP 1.7.0-1.0.6

0.17.2
------

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

0.17.1
------

_2022-03-09_

**Fix:** Fix support for nested sealed types that don't use `@JsonClass`.

0.17.0
------

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

0.16.7
------

_2022-02-01_

**moshi-ir**

* **Fix:** Use `FilesSubpluginOption` to fix build cache relocatability when generating proguard rules.

0.16.6
------

_2022-01-27_

**moshi-ir**

* **Fix:** Nested type argument use in properties would fail in 0.16.5's new type rendering. This is now fixed.
  Example failing case would've been something like this:
  ```kotlin
  @JsonClass(generateAdapter = true)
  class Foo<T>(val value: List<T>)
  ```

0.16.5
------

_2022-01-20_

* **Enhancement:** Generate manual `Type` construction in `moshi-ir` adapter lookups. Prior to this, we generated IR
  code that leveraged `typeOf()`, but this appears to be too late to leverage compiler intrinsics support for it and
  appears to cause some issues if `kotlin-reflect` is on the classpath. This should improve runtime performance as a
  result.

0.16.4
------

_2022-01-13_

* **Fix:** Add moshi/moshi-sealed-runtime dependencies as `implementation` rather than `api` when applying the
  `moshi-ir` plugin. [#200](https://github.com/ZacSweers/MoshiX/pull/200)
* **Fix:** A second attempt at fixing extension point issues with AnalysisHandlerExtension in `moshi-ir`'s proguard
  rule generation. [#201](https://github.com/ZacSweers/MoshiX/pull/201)

Thanks to [@gpeal](https://github.com/gpeal) for contributing to this release!

0.16.3
------

_2022-01-11_

* **Fix:** Build new type parameters when generating classes in `moshi-ir` rather than incorrectly reuse the
  original class's parameters. Resolves [this issue](https://issuetracker.google.com/issues/213578515) (that was
  originally believed to be a Compose issue).

0.16.2
------

_2022-01-06_

* **Fix:** Pass `generateProguardRules` Gradle plugin option correctly.
* **Fix:** Best-effort avoid synchronization race with IntelliJ openapi when registering proguard rule gen extension

0.16.1
------

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

0.16.0
------

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

0.15.0
------

_2021-12-10_

* Update to Moshi `1.13.0`
* **Removed:** The `moshi-ksp` artifact has been upstreamed to Moshi itself as is no longer published.
* **Removed:** The `moshi-records-reflect` artifact has been upstreamed to Moshi itself as is no longer published.
* Update to Kotlin `1.6.0`
* Update to KotlinPoet `1.10.2`

0.14.1
------

_2021-09-21_

* Build against JDK 17.
  * This means that `moshi-sealed-java-sealed-reflect`'s support of `sealed` classes in Java is now out of preview
    and requires Java 17 to use.
  * `moshi-records-reflect` still targets Java 16 for maximum compatibility.
  * All other artifacts still target Java 8.
* Update Kotlin to `1.5.31`
* Update KotlinPoet to `1.10.1`

0.14.0
------

_2021-09-07_

* Update KSP to `1.5.30-1.0.0` stable!
* `moshi-sealed-ksp` has now been merged into `moshi-sealed-codegen`. This artifact can be used for both `kapt` and
  `ksp`.
* `moshi-ksp` is now _soft-deprecated_ and will be fully deprecated once Moshi's next release is out with formal support.

0.13.0
------

_2021-08-27_

* Update Kotlin to `1.5.30`.
* Update KSP to `1.5.30-1.0.0-beta08`.
* **Enhancement:** `RecordsJsonAdapterFactory` is now aligned with the upstreamed implementation on Moshi itself.
  * Note that this is now _soft-deprecated_ and will be fully deprecated once Moshi's next release is out with formal support.
  * This includes using a few more modern language APIs like `MethodHandle` and better unpacking of different runtime exceptions. Full details can be found in the [PR](https://github.com/square/moshi/pull/1381).
* **Fix:** Avoid implicitly converting elements to KotlinPoet in `CodeBlock`s to avoid noisy logging.
* **Fix:** Improve self-referencing type variables parsing in `moshi-ksp` (see [#125](https://github.com/ZacSweers/MoshiX/pull/125) and [#151](https://github.com/ZacSweers/MoshiX/pull/151)).

Special thanks to [@yigit](https://github.com/yigit) for contributing to this release!

0.12.2
------

_2021-08-20_

* **Fix:** `RecordsJsonAdapterFactory` now properly respects `@JsonQualifier` annotations on components.
* **Fix:** `RecordsJsonAdapterFactory` now supports non-public constructors (i.e. package or file-private).
* **Fix:** Crash in `moshi-ksp` when dealing with generic typealias properties.

0.12.1
------

_2021-08-19_

* Update to KSP `1.5.21-1.0.0-beta07`.
* **Fix:** Previously if you had a `@JsonClass`-annotated Java file with a custom generator, `moshi-ksp` would error
  out anyway due to it not being a Kotlin class. This is now fixed and it will safely ignore these files.
* **Fix:** Generate missing `@OptIn(ExperimentalStdLibApi::class)` annotations in `moshi-sealed` when `object`
  adapters are used, as we use Moshi's reified `addAdapter` extension.

Thanks to [@gabrielittner](https://github.com/gabrielittner) for contributing to this release!

0.12.0
------

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

0.11.2
------

_2021-05-31_

* `moshi-ksp` - Fix a bug where supertypes compiled outside the current compilation weren't recognized as Kotlin types.

0.11.1
------

_2021-05-27_

* Update to KSP `1.5.10-1.0.0-beta01`
* Update to Kotlin `1.5.10`

0.11.0
------

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

0.10.0
-----

_2021-04-09_

* Update KSP to `1.4.32-1.0.0-alpha07`.
* `moshi-ksp` - Report missing primary constructor JVM signatures to `KSPLogger`.
* Update Kotlin to `1.4.32`.
* Update Moshi to `1.12.0`.

0.9.2
-----

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

0.9.1
-----

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

0.9.0
-----

This version had a bug in releasing, please ignore.

0.8.0
-----------

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

0.7.1
-----

_2021-01-11_

* Update to KSP `1.4.20-dev-experimental-20210111`.

0.7.0
-----

_2020-12-26_

This introduces support for KSP's new incremental processing support. Because all outputs in both
`moshi-ksp` and `moshi-sealed`'s `codegen-ksp`, both of them are effectively "isolating" processors.

Note that incremental processing itself is _not_ enabled by default and must be enabled via
`ksp.incremental=true` Gradle property. See KSP's release notes for more details:
https://github.com/google/ksp/releases/tag/1.4.20-dev-experimental-20201222

* KSP `1.4.20-dev-experimental-20201222`
* Kotlin `1.4.20`

0.6.1
-----

_2020-11-12_

`moshi-ksp` and `moshi-sealed-ksp` are now built against KSP version `1.4.10-dev-experimental-20201110`.

0.6.0
-----

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

0.5.0
-----

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

0.4.0
-----

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

0.3.2
-----

_2020-10-01_

Fixes two issues with `moshi-ksp`:
- Handle `Any` superclasses when the supertype is from another module
- Filter out non-`CLASS` kinds from supertypes

Special thanks to [@JvmName](https://github.com/JvmName) for reporting and helping debug this!

0.3.1
-----

_2020-09-30_

`moshi-ksp` now fully supports nullable generic types, which means it is now at feature parity with
Moshi's annotation-processor-based code gen 🥳

0.3.0
-----

_2020-09-27_

### THIS IS BIG

This project is now **MoshiX** and contains multiple Moshi extensions.

* **New:** [moshi-ksp](https://github.com/ZacSweers/MoshiX/blob/main/moshi-ksp/README.md) - A [KSP](https://github.com/google/ksp) implementation of Moshi Kotlin Codegen.
* **New:** [moshi-ktx](https://github.com/ZacSweers/MoshiX/blob/main/moshi-ktx/README.md) - Kotlin extensions for Moshi with no kotlin-reflect requirements and fully compatible with generic reified types via the stdlib's `typeOf()` API.
* **New:** [moshi-metadata-reflect](https://github.com/ZacSweers/MoshiX/blob/main/moshi-metadata-reflect/README.md) - A [kotlinx-metadata](https://github.com/JetBrains/kotlin/tree/master/libraries/kotlinx-metadata/jvm) based implementation of `KotlinJsonAdapterFactory`. This allows for reflective Moshi serialization on Kotlin classes without the cost of including kotlin-reflect.
* **Updated:** [moshi-sealed](https://github.com/ZacSweers/MoshiX/blob/main/moshi-sealed/README.md) - Largely unchanged, but now there is a new `moshi-sealed-ksp` artifact available for KSP users.

Some of these will eventually move to Moshi directly. This project going forward is a focused set of extensions that
either don't belong in Moshi directly or can be a non-API-stable testing ground for early adopters.

0.2.0
-----

_2020-04-26_

* Fix reflect artifact depending on a snapshot Moshi version.
* Update to Kotlin 1.3.72
* Update to KotlinPoet 1.5.0

0.1.0
-----

_2019-10-29_

Initial release!
