# moshi-adapters

Moshi JsonAdapters for [kotlinx.collections.immutable](https://github.com/Kotlin/kotlinx.collections.immutable) collection types

## Usage

Gradle dependency

```kotlin
dependencies {
  implementation("dev.zacsweers.moshix:moshi-adapters-kotlinx-immutable:<version>")
}
```
## Immutable Collection Adapters

Use `addKotlinXImmutableAdapters` to add `PersistentListJsonAdapterFactory` and `PersistentMapJsonAdapterFactory` type
adapters. These will deserialize standard JSON list and dictionary types into [kotlinx.collections.immutable](https://github.com/Kotlin/kotlinx.collections.immutable)
`PersistentList` and `PersistentMap` types.

```Kotlin
val moshi = Moshi.Builder()
  .addKotlinXImmutableAdapters()
  .build()

@JsonClass(generateAdapter = true)
data class MyImmutableModel(
  val stringList: PersistentList<String>,
  val objList: PersistentList<SomeObject>,
  val stringMap: PersistentMap<String, String>,
  val objMap: PersistentMap<String, SomeObject>,  
)
```
