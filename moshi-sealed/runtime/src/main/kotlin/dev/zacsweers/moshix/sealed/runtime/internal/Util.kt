package dev.zacsweers.moshix.sealed.runtime.internal

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException

public object Util {
  /**
   * Loads the generated JsonAdapter for classes annotated [JsonClass]. This works because it uses
   * the same naming conventions as `JsonClassCodeGenProcessor`.
   */
  @JvmStatic
  public fun Moshi.fallbackAdapter(adapterClass: Class<out JsonAdapter<*>>): JsonAdapter<Any>? {
    return try {
      var constructor: Constructor<out JsonAdapter<*>>
      var args: Array<Any>
      try {
        // Common case first
        constructor = adapterClass.getDeclaredConstructor(Moshi::class.java)
        args = arrayOf(this)
      } catch (e: NoSuchMethodException) {
        constructor = adapterClass.getDeclaredConstructor()
        args = emptyArray()
      }
      constructor.isAccessible = true
      @Suppress("UNCHECKED_CAST")
      constructor.newInstance(*args).nullSafe() as JsonAdapter<Any>?
    } catch (e: IllegalAccessException) {
      throw RuntimeException("Failed to access fallback adapter $adapterClass", e)
    } catch (e: InstantiationException) {
      throw RuntimeException("Failed to instantiate fallback adapter $adapterClass", e)
    } catch (e: InvocationTargetException) {
      throw e.rethrowCause()
    }
  }

  /** Throws the cause of `e`, wrapping it if it is checked. */
  private fun InvocationTargetException.rethrowCause(): RuntimeException {
    val cause = targetException
    if (cause is RuntimeException) throw cause
    if (cause is Error) throw cause
    throw RuntimeException(cause)
  }
}
