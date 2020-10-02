Changelog
=========

Version 0.3.2
-------------

_2020-10-01_

Fixes two issues with `moshi-ksp`:
- Handle `Any` superclasses when the supertype is from another module
- Filter out non-`CLASS` kinds from supertypes

Special thanks to [@JvmName][https://github.com/JvmName] for reporting and helping debug this!

Version 0.3.1
-------------

_2020-09-30_

`moshi-ksp` now fully supports nullable generic types, which means it is now at feature parity with
Moshi's annotation-processor-based code gen 🥳

Version 0.3.0
-------------

_2020-09-27_

### THIS IS BIG

This project is now **MoshiX** and contains multiple Moshi extensions.

* **New:** [moshi-ksp](https://github.com/ZacSweers/MoshiX/blob/main/moshi-ksp/README.md) - A [KSP](https://github.com/google/ksp) implementation of Moshi Kotlin Codegen.
* **New:** [moshi-ktx](https://github.com/ZacSweers/MoshiX/blob/main/moshi-ktx/README.md) - Kotlin extensions for Moshi with no kotlin-reflect requirements and fully compatible with generic reified types via the stdlib's `typeOf()` API.
* **New:** [moshi-metadata-reflect](https://github.com/ZacSweers/MoshiX/blob/main/moshi-metadata-reflect/README.md) - A [kotlinx-metadata](https://github.com/JetBrains/kotlin/tree/master/libraries/kotlinx-metadata/jvm) based implementation of `KotlinJsonAdapterFactory`. This allows for reflective Moshi serialization on Kotlin classes without the cost of including kotlin-reflect.
* **Updated:** [moshi-sealed](https://github.com/ZacSweers/MoshiX/blob/main/moshi-sealed/README.md) - Largely unchanged, but now there is a new `moshi-sealed-ksp` artifact available for KSP users.

Some of these will eventually move to Moshi directly. This project going forward is a focused set of extensions that 
either don't belong in Moshi directly or can be a non-API-stable testing ground for early adopters.

Version 0.2.0
-------------

_2020-04-26_

* Fix reflect artifact depending on a snapshot Moshi version.
* Update to Kotlin 1.3.72
* Update to KotlinPoet 1.5.0

Version 0.1.0
-------------

_2019-10-29_

Initial release!
