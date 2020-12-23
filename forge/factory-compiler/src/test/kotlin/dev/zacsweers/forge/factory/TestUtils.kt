package dev.zacsweers.forge.factory

import com.tschuchort.compiletesting.KotlinCompilation.Result
import java.lang.reflect.Member
import java.lang.reflect.Modifier
import java.util.Locale

internal val Result.daggerModule1: Class<*>
  get() = classLoader.loadClass("test.DaggerModule1")

internal val Result.daggerModule2: Class<*>
  get() = classLoader.loadClass("test.DaggerModule2")

internal val Result.daggerModule3: Class<*>
  get() = classLoader.loadClass("test.DaggerModule3")

internal val Result.daggerModule4: Class<*>
  get() = classLoader.loadClass("test.DaggerModule4")

internal fun Class<*>.moduleFactoryClass(
  providerMethodName: String,
  companion: Boolean = false
): Class<*> {
  val companionString = if (companion) "_Companion" else ""
  val enclosingClassString = enclosingClass?.let { "${it.simpleName}_" } ?: ""

  return classLoader.loadClass(
    "${`package`.name}.$enclosingClassString$simpleName$companionString" +
      "_${providerMethodName.capitalize(Locale.US)}Factory"
  )
}

internal fun Class<*>.factoryClass(): Class<*> {
  val enclosingClassString = enclosingClass?.let { "${it.simpleName}_" } ?: ""

  return classLoader.loadClass("${`package`.name}.$enclosingClassString${simpleName}_Factory")
}

internal fun Class<*>.membersInjector(): Class<*> {
  val enclosingClassString = enclosingClass?.let { "${it.simpleName}_" } ?: ""

  return classLoader.loadClass("${`package`.name}." +
    "$enclosingClassString${simpleName}_MembersInjector")
}

internal val Result.innerModule: Class<*>
  get() = classLoader.loadClass("test.ComponentInterface\$InnerModule")

val Member.isStatic: Boolean get() = Modifier.isStatic(modifiers)
val Member.isAbstract: Boolean get() = Modifier.isAbstract(modifiers)

fun <T : Any> Class<T>.newInstanceNoArgs(): T = getDeclaredConstructor().newInstance()