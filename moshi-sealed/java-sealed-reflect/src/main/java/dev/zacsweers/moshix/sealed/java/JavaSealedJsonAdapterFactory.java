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
package dev.zacsweers.moshix.sealed.java;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonClass;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory;
import dev.zacsweers.moshix.sealed.annotations.DefaultNull;
import dev.zacsweers.moshix.sealed.annotations.TypeLabel;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/** A {@link JsonAdapter.Factory} that supports Java 17+ {@code sealed} classes via reflection. */
public final class JavaSealedJsonAdapterFactory implements JsonAdapter.Factory {

  private static final String SEALED_PREFIX = "sealed:";
  private static final int SEALED_PREFIX_LENGTH = SEALED_PREFIX.length();
  private static final Object UNSET = new Object();

  @Override
  public JsonAdapter<?> create(Type type, Set<? extends Annotation> annotations, Moshi moshi) {
    if (!annotations.isEmpty()) {
      return null;
    }

    var rawType = Types.getRawType(type);
    if (!rawType.isSealed()) {
      return null;
    }
    var labelKey = labelKey(rawType.getAnnotation(JsonClass.class));
    if (labelKey == null) {
      return null;
    }
    var defaultObject = UNSET;
    if (rawType.isAnnotationPresent(DefaultNull.class)) {
      defaultObject = null;
    }

    var labels = new LinkedHashMap<String, Class<?>>();
    for (var sealedSubclassDesc : rawType.getPermittedSubclasses()) {
      // TODO check for default object annotations - they don't work here!
      try {
        var sealedSubclass = Class.forName(toBinaryName(sealedSubclassDesc.descriptorString()));
        walkTypeLabels(sealedSubclass, labelKey, labels);
      } catch (ClassNotFoundException e) {
        e.printStackTrace();
        return null;
      }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    PolymorphicJsonAdapterFactory<Object> factory =
        PolymorphicJsonAdapterFactory.of((Class) rawType, labelKey);
    for (var entry : labels.entrySet()) {
      factory = factory.withSubtype(entry.getValue(), entry.getKey());
    }
    if (defaultObject != UNSET) {
      factory = factory.withDefaultValue(null);
    }

    return factory.create(rawType, annotations, moshi);
  }

  private static String toBinaryName(String descriptor) {
    return descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
  }

  @Nullable
  private static String labelKey(@Nullable JsonClass jsonClass) {
    if (jsonClass == null) {
      return null;
    }
    var generator = jsonClass.generator();
    if (!generator.startsWith("sealed:")) {
      return null;
    }
    return generator.substring(SEALED_PREFIX_LENGTH);
  }

  private static void walkTypeLabels(
      Class<?> subtype, String labelKey, Map<String, Class<?>> labels) {
    // If it's sealed, check if it's inheriting from our existing type or a separate/new branching
    // off
    // point
    if (subtype.isSealed()) {
      var nestedLabelKey = labelKey(subtype.getAnnotation(JsonClass.class));
      if (nestedLabelKey != null) {
        // Redundant case
        if (nestedLabelKey.equals(labelKey)) {
          throw new IllegalStateException(
              "Sealed subtype %s is redundantly annotated with @JsonClass(generator = \"sealed:%s\")."
                  .formatted(subtype, nestedLabelKey));
        } else {
          // It's a different type, allow it to be used as a label
          addLabelKeyForType(subtype, labels, /* skipJsonClassCheck */ true);
        }
      } else {
        // Recurse, inheriting the top type
        for (var nested : subtype.getPermittedSubclasses()) {
          walkTypeLabels(nested, labelKey, labels);
        }
      }
    } else {
      addLabelKeyForType(subtype, labels, /* skipJsonClassCheck */ false);
    }
    // else add label
  }

  private static void addLabelKeyForType(
      Class<?> sealedSubclass, Map<String, Class<?>> labels, boolean skipJsonClassCheck) {
    var labelAnnotation = sealedSubclass.getAnnotation(TypeLabel.class);
    if (labelAnnotation == null) {
      throw new IllegalStateException(
          "Sealed subtypes must be annotated with @TypeLabel to define their label %s"
              .formatted(sealedSubclass.getCanonicalName()));
    }

    if (sealedSubclass.getTypeParameters().length > 0) {
      throw new IllegalStateException(
          "Moshi-sealed subtypes cannot be generic: %s"
              .formatted(sealedSubclass.getCanonicalName()));
    }

    var label = labelAnnotation.label();
    var prevMain = labels.put(label, sealedSubclass);
    if (prevMain != null) {
      throw new IllegalStateException(
          "Duplicate label '%s' defined for %s and %s.".formatted(label, sealedSubclass, prevMain));
    }
    for (var alternate : labelAnnotation.alternateLabels()) {
      var prev = labels.put(alternate, sealedSubclass);
      if (prev != null) {
        throw new IllegalStateException(
            "Duplicate alternate label '%s' defined for %s and %s."
                .formatted(label, sealedSubclass, prev));
      }
    }
    if (!skipJsonClassCheck && labelKey(sealedSubclass.getAnnotation(JsonClass.class)) != null) {
      throw new IllegalStateException(
          "Sealed subtype $subtype is annotated with @JsonClass(generator = \"sealed:.."
              + ".\") and @TypeLabel.");
    }
  }
}
