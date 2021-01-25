package dev.zacsweers.moshix.sealed.java;

import com.google.common.collect.ImmutableMap;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonClass;
import com.squareup.moshi.JsonDataException;
import com.squareup.moshi.Moshi;

import org.junit.Test;

import java.io.IOException;
import java.util.Map;

import dev.zacsweers.moshix.records.RecordsJsonAdapterFactory;
import dev.zacsweers.moshix.sealed.annotations.DefaultNull;
import dev.zacsweers.moshix.sealed.annotations.TypeLabel;

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

  @JsonClass(generateAdapter = true, generator = "sealed:type")
  public sealed interface MessageInterface
      permits MessageInterface.Success, MessageInterface.Error {

    @TypeLabel(label = "success", alternateLabels = {"successful"})
    final record Success(String value) implements MessageInterface {
    }

    @TypeLabel(label = "error")
    final record Error(Map<String, Object> error_logs) implements MessageInterface {
    }
  }

  @JsonClass(generateAdapter = true, generator = "sealed:type")
  public static sealed class MessageClass permits MessageClass.Success, MessageClass.Error {

    @TypeLabel(label = "success", alternateLabels = {"successful"})
    static final class Success extends MessageClass {

      final String value;

      Success(String value) {
        this.value = value;
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }

        Success success = (Success) o;

        return value != null ? value.equals(success.value) : success.value == null;
      }

      @Override
      public int hashCode() {
        return value != null ? value.hashCode() : 0;
      }
    }

    @TypeLabel(label = "error")
    static final class Error extends MessageClass {

      final Map<String, Object> error_logs;

      Error(Map<String, Object> error_logs) {
        this.error_logs = error_logs;
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }

        Error error = (Error) o;

        return error_logs != null ? error_logs.equals(error.error_logs) : error.error_logs == null;
      }

      @Override
      public int hashCode() {
        return error_logs != null ? error_logs.hashCode() : 0;
      }
    }
  }

  @DefaultNull
  @JsonClass(generateAdapter = true, generator = "sealed:type")
  public sealed interface MessageWithNullDefault
      permits MessageWithNullDefault.Success, MessageWithNullDefault.Error {

    @TypeLabel(label = "success", alternateLabels = {"successful"})
    final record Success(String value) implements MessageWithNullDefault {
    }

    @TypeLabel(label = "error")
    final record Error(Map<String, Object> error_logs) implements
        MessageWithNullDefault {
    }
  }
}
