Moshi-IR
========

A Kotlin IR implementation of Moshi code gen.

The goal of this is to have functional parity with Moshi's native Kapt/KSP code gen but run as a fully embedded IR 
plugin.

**Benefits**
- Significantly faster build times
- No reflection required at runtime to support default parameter values
- Feature parity with Moshi's native code gen

**Cons**
- No support for Proguard file generation for now. You will need to add this manually to your rules if you use 
  R8/Proguard.
  - One option is to use IR in debug builds and Kapt/KSP in release builds, the latter of which do still generate
    proguard rules.
  ```proguard
  # Keep names for JsonClass-annotated classes
  -keepnames class @com.squareup.moshi.JsonClass **

  # Keep generated adapter classes' constructors
  -keepclassmembers class *JsonAdapter {
      public <init>(...);
  }
  ```
- Kotlin IR is not a stable API and may change in future Kotlin versions. While I'll try to publish quickly to adjust to
these, you should be aware. If you have any issues, you can always fall back to Kapt/KSP.

### Installation

Simply apply the Gradle plugin in your project to use it.

The Gradle plugin is published to Maven Central, so ensure you have `mavenCentral()` visible to your buildscript 
classpath.

[![Maven Central](https://img.shields.io/maven-central/v/dev.zacsweers.moshix/moshi-gradle-plugin.svg)](https://mvnrepository.com/artifact/dev.zacsweers.moshix/moshi-sealed-runtime)
```gradle
plugins {
  kotlin("jvm")
  id("dev.zacsweers.moshix") version "x.y.z"
}
```

Snapshots of the development version are available in [Sonatype's snapshots repository][snapshots].

License
-------

    Copyright (C) 2021 Zac Sweers

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

 [snapshots]: https://oss.sonatype.org/content/repositories/snapshots/dev/zacsweers/moshix/
