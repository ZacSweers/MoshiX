# moshi-ksp

A [KSP](https://github.com/google/ksp) implementation of Moshi Kotlin Codegen. This is experimental
insofar as KSP itself is experimental, but it passes all the same tests that Moshi's existing 
annotation-processor-based code gen does.

## Usage

Add this dependency as a `ksp` dependency instead of the `moshi-kotlin-codegen` dependency.

[![Maven Central](https://img.shields.io/maven-central/v/dev.zacsweers.moshix/moshi-ksp.svg)](https://mvnrepository.com/artifact/dev.zacsweers.moshix/moshi-ksp)
```diff
dependencies {
-  kapt("com.squareup.moshi:moshi-kotlin-codegen:<version>")
+  ksp("dev.zacsweers.moshix:moshi-ksp:<version>")
}
```

To add `@Generated` annotations, specify them via ksp arguments in Gradle.

```kotlin
ksp {
  arg("moshi.generated", "javax.annotation.Generated")
}
```
