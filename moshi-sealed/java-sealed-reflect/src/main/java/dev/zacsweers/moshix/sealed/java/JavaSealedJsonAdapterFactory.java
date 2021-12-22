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
import java.util.Set;

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
    var jsonClass = rawType.getAnnotation(JsonClass.class);
    if (jsonClass == null) {
      return null;
    }
    var generator = jsonClass.generator();
    if (!generator.startsWith("sealed:")) {
      return null;
    }
    var typeLabel = generator.substring(SEALED_PREFIX_LENGTH);
    var defaultObject = UNSET;
    if (rawType.isAnnotationPresent(DefaultNull.class)) {
      defaultObject = null;
    }

    var labels = new LinkedHashMap<String, Class<?>>();
    for (var sealedSubclassDesc : rawType.getPermittedSubclasses()) {
      // TODO check for default object annotations - they don't work here!
      try {
        var sealedSubclass = Class.forName(toBinaryName(sealedSubclassDesc.descriptorString()));
        var labelAnnotation = sealedSubclass.getAnnotation(TypeLabel.class);
        if (labelAnnotation == null) {
          throw new IllegalStateException(
              "Sealed subtypes must be annotated with @TypeLabel to define their label "
                  + sealedSubclass.getCanonicalName());
        }

        if (sealedSubclass.getTypeParameters().length > 0) {
          throw new IllegalStateException(
              "Moshi-sealed subtypes cannot be generic: " + sealedSubclass.getCanonicalName());
        }

        var label = labelAnnotation.label();
        var prevMain = labels.put(label, sealedSubclass);
        if (prevMain != null) {
          throw new IllegalStateException(
              "Duplicate label '"
                  + label
                  + "' defined for "
                  + sealedSubclass
                  + " and "
                  + prevMain
                  + ".");
        }
        for (var alternate : labelAnnotation.alternateLabels()) {
          var prev = labels.put(alternate, sealedSubclass);
          if (prev != null) {
            throw new IllegalStateException(
                "Duplicate alternate label '"
                    + label
                    + "' defined for "
                    + sealedSubclass
                    + " and "
                    + prev
                    + ".");
          }
        }
      } catch (ClassNotFoundException e) {
        e.printStackTrace();
        return null;
      }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    PolymorphicJsonAdapterFactory<Object> factory =
        PolymorphicJsonAdapterFactory.of((Class) rawType, typeLabel);
    for (var entry : labels.entrySet()) {
      factory = factory.withSubtype(entry.getValue(), entry.getKey());
    }
    if (defaultObject != UNSET) {
      factory = factory.withDefaultValue(defaultObject);
    }

    return factory.create(rawType, annotations, moshi);
  }

  private static String toBinaryName(String descriptor) {
    return descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
  }
}
