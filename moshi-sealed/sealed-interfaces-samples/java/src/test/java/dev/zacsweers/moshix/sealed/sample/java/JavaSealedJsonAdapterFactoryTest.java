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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonDataException;
import com.squareup.moshi.Moshi;
import dev.zacsweers.moshix.records.RecordsJsonAdapterFactory;
import dev.zacsweers.moshix.sealed.java.JavaSealedJsonAdapterFactory;
import java.io.IOException;
import org.junit.Test;

public final class JavaSealedJsonAdapterFactoryTest {

  private final Moshi moshi =
      new Moshi.Builder()
          .add(new JavaSealedJsonAdapterFactory())
          .add(new RecordsJsonAdapterFactory())
          .build();

  @Test
  public void assertDefaultBehavior_interface() throws IOException {
    var adapter = moshi.adapter(MessageInterface.class);
    assertPolymorphicBehavior(
        adapter,
        new MessageInterface.Success("Okay!"),
        new MessageInterface.Error(ImmutableMap.of("order", 66.0)),
        false);
  }

  @Test
  public void assertDefaultBehavior_class() throws IOException {
    var adapter = moshi.adapter(MessageClass.class);
    assertPolymorphicBehavior(
        adapter,
        new MessageClass.Success("Okay!"),
        new MessageClass.Error(ImmutableMap.of("order", 66.0)),
        false);
  }

  @Test
  public void assertDefaultNullBehavior() throws IOException {
    var adapter = moshi.adapter(MessageWithNullDefault.class);
    assertPolymorphicBehavior(
        adapter,
        new MessageWithNullDefault.Success("Okay!"),
        new MessageWithNullDefault.Error(ImmutableMap.of("order", 66.0)),
        true);
  }

  @Test
  public void duplicateLabels() {
    try {
      //noinspection ResultOfMethodCallIgnored
      moshi.adapter(FailureTestCases.DuplicateLabels.class);
      fail();
    } catch (IllegalStateException e) {
      assertThat(e).hasMessageThat().contains("Duplicate label");
    }
  }

  @Test
  public void duplicateAlternateLabels() {
    try {
      //noinspection ResultOfMethodCallIgnored
      moshi.adapter(FailureTestCases.DuplicateAlternateLabels.class);
      fail();
    } catch (IllegalStateException e) {
      assertThat(e).hasMessageThat().contains("Duplicate alternate label");
    }
  }

  @Test
  public void genericSubtypes() {
    try {
      //noinspection ResultOfMethodCallIgnored
      moshi.adapter(FailureTestCases.GenericSubtypes.class);
      fail();
    } catch (IllegalStateException e) {
      assertThat(e)
          .hasMessageThat()
          .contains(
              "Moshi-sealed subtypes cannot be generic: dev.zacsweers.moshix.sealed.sample.java.FailureTestCases.GenericSubtypes.TypeB");
    }
  }

  private static <T> void assertPolymorphicBehavior(
      JsonAdapter<T> adapter, T success, T error, boolean defaultNull) throws IOException {
    assertThat(adapter.fromJson("{\"type\":\"success\",\"value\":\"Okay!\"}")).isEqualTo(success);
    // Test alternates
    assertThat(adapter.fromJson("{\"type\":\"successful\",\"value\":\"Okay!\"}"))
        .isEqualTo(success);
    assertThat(adapter.fromJson("{\"type\":\"error\",\"error_logs\":{\"order\":66}}"))
        .isEqualTo(error);

    if (defaultNull) {
      assertThat(adapter.fromJson("{\"type\":\"taco\",\"junkdata\":100}")).isNull();
    } else {
      try {
        //noinspection ResultOfMethodCallIgnored
        adapter.fromJson("{\"type\":\"taco\",\"junkdata\":100}");
        fail();
      } catch (JsonDataException e) {
        assertThat(e).hasMessageThat().contains("Register a subtype for this label");
      }
    }
  }
}
