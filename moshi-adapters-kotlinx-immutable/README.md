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

Add `ImmutableCollectionJsonAdapterFactory` to `Moshi`'s builder. These will deserialize standard JSON 
list and dictionary types into [kotlinx.collections.immutable](https://github.com/Kotlin/kotlinx.collections.immutable)
`Persistent*` and `Immutable*` types.

```Kotlin
val moshi = Moshi.Builder()
  .add(ImmutableCollectionJsonAdapterFactory())
  .build()

@JsonClass(generateAdapter = true)
data class MyImmutableModel(
  val stringList: ImmutableList<String>, // or PersistentList<String>,
  val objList: ImmutableList<SomeObject>, // or PersistentList<SomeObject>,
  val objCollection: ImmutableCollection<SomeObject>, // or PersistentCollection<SomeObject>,
  val objMap: ImmutableMap<String, SomeObject>, // or PersistentMap<String, SomeObject>,
  val objectsSet: ImmutableMap<SomeObject>, // or PersistentMap<SomeObject>,  
)
```
