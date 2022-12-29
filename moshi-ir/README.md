Moshi-IR
========

A Kotlin IR implementation of Moshi code gen and moshi-sealed code gen.

The goal of this is to have functional parity with their native Kapt/KSP code gen analogues but run as a fully 
embedded IR plugin.

**Benefits**
- Significantly faster build times.
  - No extra Kapt or KSP tasks, no extra source files to compile. This runs directly in kotlinc and generates IR 
    that is lowered directly into bytecode.
- No reflection required at runtime to support default parameter values.
- Feature parity with Moshi's native code gen.
- More detailed error messages for unexpected null values and missing properties. Now all errors are accumulated and 
  reported at the end, rather than failing eagerly with just the first one encountered.
  - See https://github.com/square/moshi/issues/836 for more details!

**Cons**
- Kotlin IR is not a stable API and may change in future Kotlin versions. While I'll try to publish quickly to adjust to
these, you should be aware. If you have any issues, you can always fall back to Kapt/KSP.

### Installation

Simply apply the Gradle plugin in your project to use it. You can enable moshi-sealed code gen via the `moshi` 
extension.

The Gradle plugin is published to Maven Central, so ensure you have `mavenCentral()` visible to your buildscript 
classpath.

[![Maven Central](https://img.shields.io/maven-central/v/dev.zacsweers.moshix/moshi-gradle-plugin.svg)](https://mvnrepository.com/artifact/dev.zacsweers.moshix/moshi-sealed-runtime)
```gradle
plugins {
  kotlin("jvm")
  id("dev.zacsweers.moshix") version "x.y.z"
}

moshi {
  // Opt-in to enable moshi-sealed, disabled by default.
  enableSealed.set(true)
}
```

Snapshots of the development version are available in [Sonatype's snapshots repository][snapshots].

License
-------

    Copyright (C) 2021 Zac Sweers

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

 [snapshots]: https://oss.sonatype.org/content/repositories/snapshots/dev/zacsweers/moshix/
