# Moshi-Ktx

Kotlin extensions for Moshi with no kotlin-reflect requirements and fully compatible with
generic reified types via the stdlib's `typeOf()` function.

## Usage

Gradle dependency

```kotlin
dependencies {
  implementation("dev.zacsweers.moshix:moshi-ktx:<version>")
}
```

Code usage
```kotlin
val simpleIntAdapter = moshi.adapter<Int>()
val intListAdapter = moshi.adapter<List<Int>>()
val complexAdapter = moshi.adapter<Map<String, List<Map<String, Int>>>>()
```

This will eventually live in Moshi directly: https://github.com/square/moshi/pull/1202
