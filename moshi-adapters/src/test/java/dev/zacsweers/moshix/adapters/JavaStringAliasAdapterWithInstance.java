// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.adapters;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import java.io.IOException;

public final class JavaStringAliasAdapterWithInstance
    extends JsonAdapter<AdaptedByTest.StringAlias> {
  public static final JavaStringAliasAdapterWithInstance INSTANCE =
      new JavaStringAliasAdapterWithInstance("instance");

  private final String value;

  public JavaStringAliasAdapterWithInstance() {
    this("constructor");
  }

  private JavaStringAliasAdapterWithInstance(String value) {
    this.value = value;
  }

  @Override
  public AdaptedByTest.StringAlias fromJson(JsonReader reader) throws IOException {
    reader.nextString();
    return new AdaptedByTest.StringAlias(value);
  }

  @Override
  public void toJson(JsonWriter writer, AdaptedByTest.StringAlias value) throws IOException {
    if (value == null) {
      writer.nullValue();
    } else {
      writer.value(value.getValue());
    }
  }
}
