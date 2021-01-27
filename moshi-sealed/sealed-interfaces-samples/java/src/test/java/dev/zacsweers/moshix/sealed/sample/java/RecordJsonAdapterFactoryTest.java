package dev.zacsweers.moshix.sealed.sample.java;

import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;

import org.junit.Test;

import java.io.IOException;

import dev.zacsweers.moshix.records.RecordsJsonAdapterFactory;

import static com.google.common.truth.Truth.assertThat;

public final class RecordJsonAdapterFactoryTest {

  private final Moshi moshi = new Moshi.Builder()
      .add(new RecordsJsonAdapterFactory())
      .build();

  @Test
  public void genericRecord() throws IOException {
    var adapter = moshi.<GenericRecord<String>>adapter(Types.newParameterizedType(GenericRecord.class, String.class));
    assertThat(adapter.fromJson("{\"value\":\"Okay!\"}"))
        .isEqualTo(new GenericRecord<>("Okay!"));
  }

  @Test
  public void genericBoundedRecord() throws IOException {
    var adapter = moshi.<GenericBoundedRecord<Integer>>adapter(
        Types.newParameterizedType(GenericBoundedRecord.class,
        Integer.class));
    assertThat(adapter.fromJson("{\"value\":4}"))
        .isEqualTo(new GenericBoundedRecord<>(4));
  }

}
