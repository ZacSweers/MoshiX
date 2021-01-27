package dev.zacsweers.moshix.sealed.sample.java;

import com.squareup.moshi.JsonClass;

import java.util.Map;

import dev.zacsweers.moshix.sealed.annotations.TypeLabel;

// @DefaultObject is not possible in java
@JsonClass(generateAdapter = true, generator = "sealed:type")
sealed interface MessageInterface
    permits MessageInterface.Success, MessageInterface.Error {

  @TypeLabel(label = "success", alternateLabels = {"successful"})
  final record Success(String value) implements MessageInterface {
  }

  @TypeLabel(label = "error")
  final record Error(Map<String, Object> error_logs) implements MessageInterface {
  }
}
