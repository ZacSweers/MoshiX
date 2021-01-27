package dev.zacsweers.moshix.sealed.sample.java;

import com.google.common.collect.ImmutableMap;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonDataException;
import com.squareup.moshi.Moshi;

import org.junit.Test;

import java.io.IOException;

import dev.zacsweers.moshix.records.RecordsJsonAdapterFactory;
import dev.zacsweers.moshix.sealed.java.JavaSealedJsonAdapterFactory;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

public final class JavaSealedJsonAdapterFactoryTest {

  private final Moshi moshi = new Moshi.Builder()
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
        false
    );
  }

  @Test
  public void assertDefaultBehavior_class() throws IOException {
    var adapter = moshi.adapter(MessageClass.class);
    assertPolymorphicBehavior(
        adapter,
        new MessageClass.Success("Okay!"),
        new MessageClass.Error(ImmutableMap.of("order", 66.0)),
        false
    );
  }

  @Test
  public void assertDefaultNullBehavior() throws IOException {
    var adapter = moshi.adapter(MessageWithNullDefault.class);
    assertPolymorphicBehavior(
        adapter,
        new MessageWithNullDefault.Success("Okay!"),
        new MessageWithNullDefault.Error(ImmutableMap.of("order", 66.0)),
        true
    );
  }

  private static <T> void assertPolymorphicBehavior(
      JsonAdapter<T> adapter,
      T success,
      T error,
      boolean defaultNull
  ) throws IOException {
    assertThat(adapter.fromJson("{\"type\":\"success\",\"value\":\"Okay!\"}"))
        .isEqualTo(success);
    // Test alternates
    assertThat(adapter.fromJson("{\"type\":\"successful\",\"value\":\"Okay!\"}"))
        .isEqualTo(success);
    assertThat(adapter.fromJson("{\"type\":\"error\",\"error_logs\":{\"order\":66}}"))
        .isEqualTo(error);

    if (defaultNull) {
      assertThat(adapter.fromJson("{\"type\":\"taco\",\"junkdata\":100}"))
          .isNull();
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
