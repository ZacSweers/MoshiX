package dev.zacsweers.moshix.sealed.sample.java;

public record GenericBoundedRecord<T extends Number>(T value) {
}
