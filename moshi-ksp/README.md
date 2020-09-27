# moshi-ksp

A [KSP](https://github.com/google/ksp) implementation of Moshi Kotlin Codegen.

## Usage

Add this dependency as a `ksp` dependency instead of the `moshi-kotlin-codegen` dependency.

```diff
dependencies {
-  kapt("com.squareup.moshi:moshi-kotlin-codegen:<version>")
+  ksp("dev.zacsweers.moshix:moshi-ksp:<version>")
}
```

To add `@Generated` controls, add them via ksp arguments

```kotlin
ksp {
  arg("moshi.generated", "javax.annotation.Generated")
}
```

## Caveats

There are a few Moshi tests that do not yet pass due to this KSP issue with nullability of generics: https://github.com/google/ksp/issues/82

All other tests pass!
