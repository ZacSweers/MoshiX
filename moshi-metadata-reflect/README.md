# moshi-metadata-reflect

A kotlinx-metadata based implementation of KotlinJsonAdapterFactory. This allows for reflective Moshi
serialization on Kotlin classes without the cost of including kotlin-reflect.

This will eventually live in Moshi directly: https://github.com/square/moshi/pull/1183

## Usage

Gradle dependency

```kotlin
dependencies {
  implementation("dev.zacsweers.moshix:moshi-metadata-reflect:<version>")
}
```

Code usage
```kotlin
val moshi = Moshi.Builder()
  .add(MetadataKotlinJsonAdapterFactory())
  .build()
```

## Motivation

Kotlin-reflect has been a long-standing thorn for `KotlinJsonAdapter` for a number of reasons:

- It has a huge impact on binary size, both in terms of the library and the compiler impact. When removing it from the slack code base recently, it saved us nearly 10k methods just by removing it (even though only 2 were used!)
- It's super slow and heavyweight
- It has a number of tricky proguard considerations

This replaces kotlin-reflect entirely with kotlinx-metadata-reflect, the low-level metadata library we use in code gen (via kotlinpoet-metadata). It's also what the `kotlinx.reflect.lite` project is in the process of being [migrated to](https://github.com/Kotlin/kotlinx.reflect.lite/pull/12), and used in a number of other major toolchains now (Dagger, R8, etc).

### Stats

Androidx-benchmark on my serialization benchmarks project shows this is ~21.5% faster for buffer reads: https://github.com/ZacSweers/json-serialization-benchmarking/pull/10

```
AndroidBenchmark.moshi_kotlin_codegen_buffer_toJson[minified=true]                   5,052,032  ns
AndroidBenchmark.moshi_kotlin_codegen_string_toJson[minified=true]                   7,072,449  ns
AndroidBenchmark.moshi_kotlin_reflective_metadata_buffer_toJson[minified=true]       8,096,823  ns
AndroidBenchmark.moshi_kotlin_reflective_buffer_toJson[minified=true]                9,461,355  ns
AndroidBenchmark.moshi_kotlin_reflective_string_toJson[minified=true]               11,308,542  ns

AndroidBenchmark.moshi_kotlin_codegen_buffer_fromJson[minified=true]                 7,553,282  ns
AndroidBenchmark.moshi_kotlin_codegen_string_fromJson[minified=true]                 8,746,668  ns
AndroidBenchmark.moshi_kotlin_reflective_metadata_buffer_fromJson[minified=true]     9,167,501  ns
AndroidBenchmark.moshi_kotlin_codegen_buffer_fromJson[minified=false]               10,039,950  ns
AndroidBenchmark.moshi_kotlin_reflective_buffer_fromJson[minified=true]             10,454,168  ns
AndroidBenchmark.moshi_kotlin_reflective_metadata_buffer_fromJson[minified=false]   11,691,721  ns
AndroidBenchmark.moshi_kotlin_codegen_string_fromJson[minified=false]               11,716,408  ns
AndroidBenchmark.moshi_kotlin_reflective_string_fromJson[minified=true]             11,734,532  ns
AndroidBenchmark.moshi_kotlin_reflective_buffer_fromJson[minified=false]            12,930,158  ns
AndroidBenchmark.moshi_kotlin_reflective_string_fromJson[minified=false]            14,747,397  ns
```
