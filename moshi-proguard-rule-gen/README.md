Moshi Proguard Rule Gen
=======================

This is a KSP SymbolProcessor to generate proguard rules for both Moshi and moshi-sealed.

This isn't usually intended to be used directly, but rather is applied transitively in moshi-sealed's KSP processor or moshi-ir's Gradle plugin.

## Configuration

There are two modes for this processor.

1. moshi-sealed proguard gen only (the default). This mode only generates proguard rules for moshi-sealed, intended for use in tandem with moshi's native KSP processor.
2. Full proguard gen (opt-in). This mode is only for use with moshi-ir, and generates proguard rules for all generated core moshi adapters alongside moshi-sealed adapters.
   * This is controlled by the `moshi.generateCoreMoshiProguardRules` boolean argument.

Both of the above respect Moshi's native `moshi.generateProguardRules` argument, which is `true` by default.