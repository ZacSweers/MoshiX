# Moshi-Ktx

Kotlin extensions for Moshi with no kotlin-reflect requirements and fully compatible with
generic reified types via the stdlib's `typeOf()` function.

```kotlin
val simpleIntAdapter = moshi.adapter<Int>()
val intListAdapter = moshi.adapter<List<Int>>()
val complexAdapter = moshi.adapter<Map<String, List<Map<String, Int>>>>()
```