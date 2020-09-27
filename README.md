# MoshiX

Extensions for Moshi

* [moshi-sealed](https://github.com/ZacSweers/MoshiX/blob/main/moshi-sealed/README.md) - Reflective and code gen implementations for serializing Kotlin sealed classes via Moshi polymorphic adapters.
* [moshi-ksp](https://github.com/ZacSweers/MoshiX/blob/main/moshi-ksp/README.md) - A [KSP](https://github.com/google/ksp) implementation of Moshi Kotlin Codegen.
* [moshi-ktx](https://github.com/ZacSweers/MoshiX/blob/main/moshi-ktx/README.md) - Kotlin extensions for Moshi with no kotlin-reflect requirements and fully compatible with generic reified types via the stdlib's `typeOf()` API.
* [moshi-metadata-reflect](https://github.com/ZacSweers/MoshiX/blob/main/moshi-metadata-reflect/README.md) - A [kotlinx-metadata](https://github.com/JetBrains/kotlin/tree/master/libraries/kotlinx-metadata/jvm) based implementation of `KotlinJsonAdapterFactory`. This allows for reflective Moshi serialization on Kotlin classes without the cost of including kotlin-reflect.

Snapshots of the development version are available in [Sonatype's `snapshots` repository][snap].

License
--------

    Copyright 2020 Zac Sweers

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.


[snap]: https://oss.sonatype.org/content/repositories/snapshots/dev/zacsweers/moshix/
