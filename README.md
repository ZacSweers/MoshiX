# MoshiX

Extensions for [Moshi](https://github.com/square/moshi)

* [moshi-ir](https://github.com/ZacSweers/MoshiX/tree/main/moshi-ir) - A Kotlin IR implementation of Moshi and
  moshi-sealed code gen.
* [moshi-adapters](https://github.com/ZacSweers/MoshiX/tree/main/moshi-adapters) - A collection of custom adapters for Moshi.
* [moshi-metadata-reflect](https://github.com/ZacSweers/MoshiX/tree/main/moshi-metadata-reflect) - A [kotlinx-metadata](https://github.com/JetBrains/kotlin/tree/master/libraries/kotlinx-metadata/jvm) based implementation of `KotlinJsonAdapterFactory`. This allows for reflective Moshi serialization on Kotlin classes without the cost of including kotlin-reflect.
* [moshi-sealed](https://github.com/ZacSweers/MoshiX/tree/main/moshi-sealed) - Reflective and code gen implementations for serializing Kotlin sealed classes via Moshi polymorphic adapters.

Snapshots of the development version are available in [Sonatype's `snapshots` repository][snap].

### Local development

This project requires JDK 17 as a minimum due to tooling compatibility requirements to build against newer JDK
APIs.

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
