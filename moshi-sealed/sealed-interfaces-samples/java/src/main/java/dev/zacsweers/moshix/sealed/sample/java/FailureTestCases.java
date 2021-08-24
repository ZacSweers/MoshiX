/*
 * Copyright (C) 2021 Zac Sweers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.zacsweers.moshix.sealed.sample.java;

import com.squareup.moshi.JsonClass;
import dev.zacsweers.moshix.sealed.annotations.TypeLabel;

public interface FailureTestCases {
  @JsonClass(generateAdapter = false, generator = "sealed:type")
  sealed class DuplicateLabels permits DuplicateLabels.TypeA, DuplicateLabels.TypeB {
    @TypeLabel(label = "a")
    static final class TypeA extends DuplicateLabels {}

    @TypeLabel(label = "a")
    static final class TypeB extends DuplicateLabels {}
  }

  @JsonClass(generateAdapter = false, generator = "sealed:type")
  sealed class DuplicateAlternateLabels
      permits DuplicateAlternateLabels.TypeA, DuplicateAlternateLabels.TypeB {
    @TypeLabel(
        label = "a",
        alternateLabels = {"aa"})
    static final class TypeA extends DuplicateAlternateLabels {}

    @TypeLabel(
        label = "b",
        alternateLabels = {"aa"})
    static final class TypeB extends DuplicateAlternateLabels {}
  }

  @JsonClass(generateAdapter = false, generator = "sealed:type")
  sealed class GenericSubtypes<T> permits GenericSubtypes.TypeA, GenericSubtypes.TypeB {
    // This form is ok
    @TypeLabel(
        label = "a",
        alternateLabels = {"aa"})
    static final class TypeA extends GenericSubtypes<String> {}

    // This form is not ok
    @TypeLabel(
        label = "b",
        alternateLabels = {"aa"})
    static final class TypeB<T> extends GenericSubtypes<T> {}
  }
}
