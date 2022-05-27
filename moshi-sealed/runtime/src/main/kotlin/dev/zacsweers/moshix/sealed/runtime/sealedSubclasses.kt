package dev.zacsweers.moshix.sealed.runtime

import kotlin.reflect.KClass

public val <T : Any> KClass<T>.reflexiveSealedSubclasses: List<KClass<out T>>
  get() = throw NotImplementedError("implemented by the compiler plugin")