package dev.zacsweers.moshix.sealed.sample.java;

import com.squareup.moshi.JsonClass;

import java.util.Map;

import dev.zacsweers.moshix.sealed.annotations.TypeLabel;

// @DefaultObject is not possible in java
@JsonClass(generateAdapter = true, generator = "sealed:type")
public sealed class MessageClass permits MessageClass.Success, MessageClass.Error {

  @TypeLabel(label = "success", alternateLabels = {"successful"})
  static final class Success extends MessageClass {

    final String value;

    Success(String value) {
      this.value = value;
    }
  }

  @TypeLabel(label = "error")
  static final class Error extends MessageClass {

    final Map<String, Object> error_logs;

    Error(Map<String, Object> error_logs) {
      this.error_logs = error_logs;
    }
  }
}
