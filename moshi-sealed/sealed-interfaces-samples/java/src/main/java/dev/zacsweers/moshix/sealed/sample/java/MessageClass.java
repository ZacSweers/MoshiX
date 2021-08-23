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
import java.util.Map;
import java.util.Objects;

// @DefaultObject is not possible in java
@JsonClass(generateAdapter = false, generator = "sealed:type")
sealed class MessageClass permits MessageClass.Success, MessageClass.Error {

  @TypeLabel(
      label = "success",
      alternateLabels = {"successful"})
  static final class Success extends MessageClass {

    final String value;

    Success(String value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      Success success = (Success) o;

      return Objects.equals(value, success.value);
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
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      Error error = (Error) o;

      return Objects.equals(error_logs, error.error_logs);
    }

    @Override
    public int hashCode() {
      return error_logs != null ? error_logs.hashCode() : 0;
    }
  }
}
