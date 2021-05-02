#### Java `record` classes support

Experimental support for Java `record` classes via new `moshi-records-reflect` artifact. See
`RecordsJsonAdapterFactory`. Requires JDK 16+.

```java
Moshi moshi = new Moshi.Builder()
  .add(new RecordsJsonAdapterFactory())
  .build();

final record Message(String value) {
}
```

Gradle dependency:

[![Maven Central](https://img.shields.io/maven-central/v/dev.zacsweers.moshix/moshi-records-reflect.svg)](https://mvnrepository.com/artifact/dev.zacsweers.moshix/moshi-records-reflect)
```gradle
implementation "dev.zacsweers.moshix:moshi-records-reflect:{version}"
```