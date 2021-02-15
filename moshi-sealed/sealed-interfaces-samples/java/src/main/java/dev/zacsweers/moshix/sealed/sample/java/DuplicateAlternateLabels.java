package dev.zacsweers.moshix.sealed.sample.java;

import com.squareup.moshi.JsonClass;

import dev.zacsweers.moshix.sealed.annotations.TypeLabel;

@JsonClass(generateAdapter = false, generator = "sealed:type")
sealed class DuplicateAlternateLabels permits DuplicateAlternateLabels.TypeA, DuplicateAlternateLabels.TypeB {
  @TypeLabel(label = "a", alternateLabels = {"aa"})
  static final class TypeA extends DuplicateAlternateLabels {}

  @TypeLabel(label = "b", alternateLabels = {"aa"})
  static final class TypeB extends DuplicateAlternateLabels {}
}
