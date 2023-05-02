# moshi-adapters

A collection of custom adapters for Moshi.

## Usage

Gradle dependency

```kotlin
dependencies {
  implementation("dev.zacsweers.moshix:moshi-adapters:<version>")
}
```
## Adapters

### `@JsonString`

A `JsonQualifier` for use with `String` properties to indicate that their value should be
propagated with the raw JSON string representation of that property rather than a decoded type.

```Kotlin
val moshi = Moshi.Builder()
  .add(JsonString.Factory())
  .build()

@JsonClass(generateAdapter = true)
data class Message(
  val type: String,
  @JsonString val data: String
)
```

### `@AdaptedBy`

An annotation that indicates the Moshi `JsonAdapter` or `JsonAdapter.Factory` to use
with a class or property (as a `@JsonQualifier`).

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

### `@TrackUnknownKeys`

An annotation to help track unknown keys in JSON.

Note that this adapter is slow because it must parse the entire JSON object ahead of time, then writes and re-reads
it.

In general, it is not recommended to use this adapter in production unless absolutely necessary or to sample its
usage. This should be used for debugging/logging information only.

Usage:

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

