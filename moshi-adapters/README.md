# moshi-adapters

A collection of custom adapters for Moshi.

## Usage

Gradle dependency

[![Maven Central](https://img.shields.io/maven-central/v/dev.zacsweers.moshix/moshi-adapters.svg)](https://mvnrepository.com/artifact/dev.zacsweers.moshix/moshi-adapters)
```kotlin
dependencies {
  implementation("dev.zacsweers.moshix:moshi-adapters:<version>")
}
```
## Adapters

### `JsonString`

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
