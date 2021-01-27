package dev.zacsweers.moshix.records;

import com.squareup.moshi.Json;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonQualifier;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A {@link JsonAdapter.Factory} that supports Java {@code record} classes via reflection.
 * <p>
 * <em>NOTE:</em> Java records are currently in preview.
 */
public final class RecordsJsonAdapterFactory implements JsonAdapter.Factory {

  @Override
  public JsonAdapter<?> create(
      Type type, Set<? extends Annotation> annotations, Moshi moshi) {
    if (!annotations.isEmpty()) { return null; }

    if (!(type instanceof Class) && !(type instanceof ParameterizedType)) {
      return null;
    }

    var rawType = Types.getRawType(type);

    Map<String, Type> mappedTypeArgs = null;
    if (type instanceof ParameterizedType parameterizedType) {
      Type[] typeArgs = parameterizedType.getActualTypeArguments();
      var typeVars = rawType.getTypeParameters();
      mappedTypeArgs = new LinkedHashMap<>(typeArgs.length);
      for (int i = 0; i < typeArgs.length; ++i) {
        var typeVarName = typeVars[i].getName();
        var materialized = typeArgs[i];
        mappedTypeArgs.put(typeVarName, materialized);
      }
    }
    if (!rawType.isRecord()) { return null; }
    var components = rawType.getRecordComponents();
    var bindings = new LinkedHashMap<String, ComponentBinding<?>>();
    var constructorParams = new Class<?>[components.length];
    for (int i = 0, componentsLength = components.length; i < componentsLength; i++) {
      RecordComponent component = components[i];
      constructorParams[i] = component.getType();
      var name = component.getName();
      var componentType = component.getGenericType();
      if (componentType instanceof TypeVariable<?> typeVariable) {
        var typeVarName = typeVariable.getName();
        if (mappedTypeArgs == null) {
          throw new AssertionError("No mapped type arguments found for type '" + typeVarName + "'");
        }
        var mappedType = mappedTypeArgs.get(typeVarName);
        if (mappedType == null) {
          throw new AssertionError("No materialized type argument found for type '" + typeVarName + "'");
        }
        componentType = mappedType;
      }
      var jsonName = name;
      Set<Annotation> qualifiers = null;
      for (var annotation : component.getDeclaredAnnotations()) {
        if (annotation instanceof Json jsonAnnotation) {
          jsonName = jsonAnnotation.name();
        } else {
          if (annotation.annotationType().isAnnotationPresent(JsonQualifier.class)) {
            if (qualifiers == null) {
              qualifiers = new LinkedHashSet<>();
            }
            qualifiers.add(annotation);
          }
        }
      }
      var adapter = moshi.adapter(componentType, annotations);
      var accessor = component.getAccessor();
      var componentBinding = new ComponentBinding<>(name, jsonName, adapter, accessor);
      var replaced = bindings.put(jsonName, componentBinding);
      if (replaced != null) {
        throw new IllegalArgumentException(
            "Conflicting components:\n"
                + "    "
                + replaced.name
                + "\n"
                + "    "
                + componentBinding.name);
      }
    }
    Constructor<Object> constructor;
    try {
      //noinspection unchecked
      constructor = (Constructor<Object>) rawType.getConstructor(constructorParams);
    } catch (NoSuchMethodException e) {
      throw new AssertionError(e);
    }
    return new ComponentBindingAdapter<>(constructor, bindings).nullSafe();
  }

  private record ComponentBinding<T>(String name, String jsonName, JsonAdapter<T> adapter, Method accessor) {
  }

  private static class ComponentBindingAdapter<T> extends JsonAdapter<T> {

    private final String targetClass;
    private final Constructor<T> constructor;
    private final ComponentBinding<Object>[] componentBindingsArray;
    private final JsonReader.Options options;

    @SuppressWarnings("ToArrayCallWithZeroLengthArrayArgument")
    public ComponentBindingAdapter(
        Constructor<T> constructor,
        Map<String, ComponentBinding<?>> componentBindings
    ) {
      this.constructor = constructor;
      this.targetClass = constructor.getDeclaringClass().getSimpleName();
      //noinspection unchecked
      this.componentBindingsArray = componentBindings.values().toArray(new ComponentBinding[componentBindings.size()]);
      this.options = JsonReader.Options.of(componentBindings.keySet().toArray(new String[componentBindings.size()]));
    }

    @Override
    public T fromJson(JsonReader reader) throws IOException {
      var resultsArray = new Object[componentBindingsArray.length];

      reader.beginObject();
      while (reader.hasNext()) {
        int index = reader.selectName(options);
        if (index == -1) {
          reader.skipName();
          reader.skipValue();
          continue;
        }
        var result = componentBindingsArray[index].adapter.fromJson(reader);
        resultsArray[index] = result;
      }
      reader.endObject();

      try {
        return constructor.newInstance(resultsArray);
      } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
        throw new AssertionError(e);
      }
    }

    @Override
    public void toJson(JsonWriter writer, T value) throws IOException {
      writer.beginObject();

      for (var binding : componentBindingsArray) {
        writer.name(binding.jsonName);
        try {
          binding.adapter.toJson(writer, binding.accessor.invoke(value));
        } catch (IllegalAccessException | InvocationTargetException e) {
          throw new AssertionError(e);
        }
      }

      writer.endObject();
    }

    @Override
    public String toString() {
      return "JsonAdapter(" + targetClass + ")";
    }
  }
}
