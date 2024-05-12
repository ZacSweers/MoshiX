# moshi-adapters

A collection of Moshi adapters for [kotlinx.collections.immutable](https://github.com/Kotlin/kotlinx.collections.immutable).

## Usage

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
