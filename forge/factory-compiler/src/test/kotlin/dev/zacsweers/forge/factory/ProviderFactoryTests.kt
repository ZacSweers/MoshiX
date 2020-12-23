package dev.zacsweers.forge.factory

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.SourceFile.Companion.kotlin
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.symbolProcessors
import dagger.internal.Factory
import dagger.Lazy
import dagger.internal.codegen.ComponentProcessor
import dev.zacsweers.forge.factory.UppercasePackage.OuterClass
import dev.zacsweers.forge.factory.UppercasePackage.TestClassInUppercasePackage
import dev.zacsweers.forge.factory.UppercasePackage.lowerCaseClassInUppercasePackage
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.io.File
import javax.inject.Provider

@Suppress("UNCHECKED_CAST")
@RunWith(Parameterized::class)
class ProvidesMethodFactoryGeneratorTest(
  private val useDagger: Boolean
) {

  companion object {
    @Parameters(name = "Use Dagger: {0}")
    @JvmStatic
    fun useDagger(): Collection<Any> {
      return listOf(true, false)
    }
  }

  @Rule
  @JvmField
  var temporaryFolder: TemporaryFolder = TemporaryFolder()

  @Test
  fun `a factory class is generated for a provider method`() {
    /*
package test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://dagger.dev"
)
@SuppressWarnings({
  "unchecked",
  "rawtypes"
})
public final class DaggerModule1_ProvideStringFactory implements Factory<String> {
  private final DaggerModule1 module;

  public DaggerModule1_ProvideStringFactory(DaggerModule1 module) {
    this.module = module;
  }

  @Override
  public String get() {
    return provideString(module);
  }

  public static DaggerModule1_ProvideStringFactory create(DaggerModule1 module) {
    return new DaggerModule1_ProvideStringFactory(module);
  }

  public static String provideString(DaggerModule1 instance) {
    return Preconditions.checkNotNull(instance.provideString(), "Cannot return null from a non-@Nullable @Provides method");
  }
}
     */

    compile(
      """
        package test
        
        @dagger.Module
        class DaggerModule1 {
          @dagger.Provides fun provideString(): String = "abc"
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideString")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(daggerModule1)

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(2)

      val module = daggerModule1.newInstanceNoArgs()

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null, module)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideString" }
        .invoke(null, module) as String

      assertThat(providedString).isEqualTo("abc")
      assertThat((factoryInstance as Factory<String>).get()).isEqualTo("abc")
    }
  }

  @Test
  fun `a factory class is generated for a provided Factory and avoids ambiguous import`() {
    /*
package test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_ProvideFactoryFactory implements Factory<dev.zacsweers.forge.factory.Factory> {
  private final DaggerModule1 module;

  public DaggerModule1_ProvideFactoryFactory(DaggerModule1 module) {
    this.module = module;
  }

  @Override
  public dev.zacsweers.forge.factory.Factory get() {
    return provideFactory(module);
  }

  public static DaggerModule1_ProvideFactoryFactory create(DaggerModule1 module) {
    return new DaggerModule1_ProvideFactoryFactory(module);
  }

  public static dev.zacsweers.forge.factory.Factory provideFactory(DaggerModule1 instance) {
    return Preconditions.checkNotNull(instance.provideFactory(), "Cannot return null from a non-@Nullable @Provides method");
  }
}
     */
    compile(
      """
        package test

        import dev.zacsweers.forge.factory.Factory
        
        @dagger.Module
        class DaggerModule1 {
          @dagger.Provides fun provideFactory(): Factory = Factory
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideFactory")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(daggerModule1)

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(2)

      val module = daggerModule1.newInstanceNoArgs()

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null, module)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedFactory = staticMethods.single { it.name == "provideFactory" }
        .invoke(null, module) as Any

      assertThat((factoryInstance as Factory<*>).get()).isSameInstanceAs(providedFactory)
    }
  }

  @Test
  fun `a factory class is generated for a provider method with imports`() {
    /*
package test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://dagger.dev"
)
@SuppressWarnings({
  "unchecked",
  "rawtypes"
})
public final class DaggerModule1_ProvideStringFactory implements Factory<String> {
  private final DaggerModule1 module;

  public DaggerModule1_ProvideStringFactory(DaggerModule1 module) {
    this.module = module;
  }

  @Override
  public String get() {
    return provideString(module);
  }

  public static DaggerModule1_ProvideStringFactory create(DaggerModule1 module) {
    return new DaggerModule1_ProvideStringFactory(module);
  }

  public static String provideString(DaggerModule1 instance) {
    return Preconditions.checkNotNull(instance.provideString(), "Cannot return null from a non-@Nullable @Provides method");
  }
}
     */

    compile(
      """
        package test
        
        import dagger.Module
        import dagger.Provides
        
        @Module
        class DaggerModule1 {
          @Provides fun provideString(): String = "abc"
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideString")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(daggerModule1)

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(2)

      val module = daggerModule1.newInstanceNoArgs()

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null, module)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideString" }
        .invoke(null, module) as String

      assertThat(providedString).isEqualTo("abc")
      assertThat((factoryInstance as Factory<String>).get()).isEqualTo("abc")
    }
  }

  @Test
  fun `a factory class is generated for a provider method with imports and fully qualified return type`() { // ktlint-disable max-line-length
    /*
package test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import java.io.File;
import javax.annotation.Generated;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_ProvideFileFactory implements Factory<File> {
  private final DaggerModule1 module;

  public DaggerModule1_ProvideFileFactory(DaggerModule1 module) {
    this.module = module;
  }

  @Override
  public File get() {
    return provideFile(module);
  }

  public static DaggerModule1_ProvideFileFactory create(DaggerModule1 module) {
    return new DaggerModule1_ProvideFileFactory(module);
  }

  public static File provideFile(DaggerModule1 instance) {
    return Preconditions.checkNotNull(instance.provideFile(), "Cannot return null from a non-@Nullable @Provides method");
  }
}
     */

    compile(
      """
        package test
        
        import dagger.Module
        import dagger.Provides
        
        @Module
        class DaggerModule1 {
          @Provides fun provideFile(): java.io.File = java.io.File("")
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideFile")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(daggerModule1)

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(2)

      val module = daggerModule1.newInstanceNoArgs()

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null, module)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedFile = staticMethods.single { it.name == "provideFile" }
        .invoke(null, module) as File

      assertThat(providedFile).isEqualTo(File(""))
      assertThat((factoryInstance as Factory<File>).get()).isEqualTo(File(""))
    }
  }

  @Test
  fun `a factory class is generated for a provider method with star imports`() {
    /*
package test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import java.io.File;
import javax.annotation.Generated;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_ProvideFileFactory implements Factory<File> {
  private final DaggerModule1 module;

  public DaggerModule1_ProvideFileFactory(DaggerModule1 module) {
    this.module = module;
  }

  @Override
  public File get() {
    return provideFile(module);
  }

  public static DaggerModule1_ProvideFileFactory create(DaggerModule1 module) {
    return new DaggerModule1_ProvideFileFactory(module);
  }

  public static File provideFile(DaggerModule1 instance) {
    return Preconditions.checkNotNull(instance.provideFile(), "Cannot return null from a non-@Nullable @Provides method");
  }
}
     */

    compile(
      """
        package test
        
        import dagger.*
        import java.io.File
        
        @Module
        class DaggerModule1 {
          @Provides fun provideFile(): File = File("")
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideFile")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(daggerModule1)

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(2)

      val module = daggerModule1.newInstanceNoArgs()

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null, module)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedFile = staticMethods.single { it.name == "provideFile" }
        .invoke(null, module) as File

      assertThat(providedFile).isEqualTo(File(""))
      assertThat((factoryInstance as Factory<File>).get()).isEqualTo(File(""))
    }
  }

  @Test
  fun `a factory class is generated for a provider method with generic type`() {
    /*
package test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import java.util.List;
import javax.annotation.Generated;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://dagger.dev"
)
@SuppressWarnings({
  "unchecked",
  "rawtypes"
})
public final class DaggerModule1_ProvideStringListFactory implements Factory<List<String>> {
  private final DaggerModule1 module;

  public DaggerModule1_ProvideStringListFactory(DaggerModule1 module) {
    this.module = module;
  }

  @Override
  public List<String> get() {
    return provideStringList(module);
  }

  public static DaggerModule1_ProvideStringListFactory create(DaggerModule1 module) {
    return new DaggerModule1_ProvideStringListFactory(module);
  }

  public static List<String> provideStringList(DaggerModule1 instance) {
    return Preconditions.checkNotNull(instance.provideStringList(), "Cannot return null from a non-@Nullable @Provides method");
  }
}
     */

    compile(
      """
        package test
        
        @dagger.Module
        class DaggerModule1 {
          @dagger.Provides fun provideStringList(): List<String> = listOf("abc")
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideStringList")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(daggerModule1)

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(2)

      val module = daggerModule1.newInstanceNoArgs()

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null, module)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideStringList" }
        .invoke(null, module) as List<String>

      assertThat(providedString).containsExactly("abc")
      assertThat((factoryInstance as Factory<List<String>>).get()).containsExactly("abc")
    }
  }

  @Test
  fun `a factory class is generated for a provider method with advanced generics`() {
    /*
package test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import java.util.List;
import javax.annotation.Generated;
import kotlin.Pair;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_ProvidePairFactory implements Factory<Pair<Pair<String, Integer>, List<String>>> {
  private final DaggerModule1 module;

  public DaggerModule1_ProvidePairFactory(DaggerModule1 module) {
    this.module = module;
  }

  @Override
  public Pair<Pair<String, Integer>, List<String>> get() {
    return providePair(module);
  }

  public static DaggerModule1_ProvidePairFactory create(DaggerModule1 module) {
    return new DaggerModule1_ProvidePairFactory(module);
  }

  public static Pair<Pair<String, Integer>, List<String>> providePair(DaggerModule1 instance) {
    return Preconditions.checkNotNull(instance.providePair(), "Cannot return null from a non-@Nullable @Provides method");
  }
}
     */

    compile(
      """
        package test
        
        import dagger.*
        
        @Module
        class DaggerModule1 {
          @Provides fun providePair(): Pair<Pair<String, Int>, List<String>> = Pair(Pair("", 1), listOf(""))
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("providePair")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(daggerModule1)

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val module = daggerModule1.newInstanceNoArgs()

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null, module)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "providePair" }
        .invoke(null, module) as Pair<Pair<String, Int>, List<String>>

      val expected = Pair(Pair("", 1), listOf(""))
      assertThat(providedString).isEqualTo(expected)
      assertThat((factoryInstance as Factory<Pair<Pair<String, Int>, List<String>>>).get())
        .isEqualTo(expected)
    }
  }

  @Test
  fun `two factory classes are generated for two provider methods`() {
    /*
package test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://dagger.dev"
)
@SuppressWarnings({
  "unchecked",
  "rawtypes"
})
public final class DaggerModule1_ProvideStringFactory implements Factory<String> {
  private final DaggerModule1 module;

  public DaggerModule1_ProvideStringFactory(DaggerModule1 module) {
    this.module = module;
  }

  @Override
  public String get() {
    return provideString(module);
  }

  public static DaggerModule1_ProvideStringFactory create(DaggerModule1 module) {
    return new DaggerModule1_ProvideStringFactory(module);
  }

  public static String provideString(DaggerModule1 instance) {
    return Preconditions.checkNotNull(instance.provideString(), "Cannot return null from a non-@Nullable @Provides method");
  }
}
     */
    /*
package test;

import dagger.internal.Factory;
import javax.annotation.Generated;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_ProvideIntFactory implements Factory<Integer> {
  private final DaggerModule1 module;

  public DaggerModule1_ProvideIntFactory(DaggerModule1 module) {
    this.module = module;
  }

  @Override
  public Integer get() {
    return provideInt(module);
  }

  public static DaggerModule1_ProvideIntFactory create(DaggerModule1 module) {
    return new DaggerModule1_ProvideIntFactory(module);
  }

  public static int provideInt(DaggerModule1 instance) {
    return instance.provideInt();
  }
}
     */

    compile(
      """
        package test
        
        @dagger.Module
        class DaggerModule1 {
          @dagger.Provides fun provideString(): String = "abc"
          @dagger.Provides fun provideInt(): Int = 5
        }
        """
    ) {
      fun <T> verifyClassGenerated(
        providerMethodName: String,
        expectedResult: T
      ) {
        val factoryClass = daggerModule1.moduleFactoryClass(providerMethodName)

        val constructor = factoryClass.declaredConstructors.single()
        assertThat(constructor.parameterTypes.toList()).containsExactly(daggerModule1)

        val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }
        assertThat(staticMethods).hasSize(2)

        val module = daggerModule1.newInstanceNoArgs()

        val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null, module)
        assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

        val providedString = staticMethods.single { it.name == providerMethodName }
          .invoke(null, module) as T

        assertThat(providedString).isEqualTo(expectedResult)
        assertThat((factoryInstance as Factory<T>).get()).isEqualTo(expectedResult)
      }

      verifyClassGenerated("provideString", "abc")
      verifyClassGenerated("provideInt", 5)
    }
  }

  @Test
  fun `a factory class is generated for a provider method in an object`() {
    /*
package test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://dagger.dev"
)
@SuppressWarnings({
  "unchecked",
  "rawtypes"
})
public final class DaggerModule1_ProvideStringFactory implements Factory<String> {
  @Override
  public String get() {
    return provideString();
  }

  public static DaggerModule1_ProvideStringFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static String provideString() {
    return Preconditions.checkNotNull(DaggerModule1.INSTANCE.provideString(), "Cannot return null from a non-@Nullable @Provides method");
  }

  private static final class InstanceHolder {
    private static final DaggerModule1_ProvideStringFactory INSTANCE = new DaggerModule1_ProvideStringFactory();
  }
}
     */

    compile(
      """
        package test
        
        @dagger.Module
        object DaggerModule1 {
          @dagger.Provides fun provideString(): String = "abc"
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideString")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideString" }
        .invoke(null) as String

      assertThat(providedString).isEqualTo("abc")
      assertThat((factoryInstance as Factory<String>).get()).isEqualTo("abc")
    }
  }

  @Test
  fun `a factory class is generated for a provider method with parameters`() {
    /*
package test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_ProvideStringFactory implements Factory<String> {
  private final DaggerModule1 module;

  private final Provider<String> param1Provider;

  private final Provider<CharSequence> param2Provider;

  public DaggerModule1_ProvideStringFactory(DaggerModule1 module, Provider<String> param1Provider,
      Provider<CharSequence> param2Provider) {
    this.module = module;
    this.param1Provider = param1Provider;
    this.param2Provider = param2Provider;
  }

  @Override
  public String get() {
    return provideString(module, param1Provider.get(), param2Provider.get());
  }

  public static DaggerModule1_ProvideStringFactory create(DaggerModule1 module,
      Provider<String> param1Provider, Provider<CharSequence> param2Provider) {
    return new DaggerModule1_ProvideStringFactory(module, param1Provider, param2Provider);
  }

  public static String provideString(DaggerModule1 instance, String param1, CharSequence param2) {
    return Preconditions.checkNotNull(instance.provideString(param1, param2), "Cannot return null from a non-@Nullable @Provides method");
  }
}
     */

    compile(
      """
        package test
        
        import dagger.Module
        import dagger.Provides
        import javax.inject.Named
        
        @Module
        class DaggerModule1 {
          @Provides fun provideString(
            @Named("abc") param1: String, 
            param2: CharSequence 
          ): String = param1 + param2
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideString")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(daggerModule1, Provider::class.java, Provider::class.java)
        .inOrder()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val module = daggerModule1.newInstanceNoArgs()

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null, module, Provider { "a" }, Provider<CharSequence> { "b" })
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideString" }
        .invoke(null, module, "a", "b" as CharSequence) as String

      assertThat(providedString).isEqualTo("ab")
      assertThat((factoryInstance as Factory<String>).get()).isEqualTo("ab")
    }
  }

  @Test
  fun `a factory class is generated for a provider method with provider parameters`() {
    /*
package test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import java.util.List;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_ProvideStringFactory implements Factory<String> {
  private final DaggerModule1 module;

  private final Provider<String> param1Provider;

  private final Provider<CharSequence> param2Provider;

  private final Provider<List<String>> param3Provider;

  public DaggerModule1_ProvideStringFactory(DaggerModule1 module, Provider<String> param1Provider,
      Provider<CharSequence> param2Provider, Provider<List<String>> param3Provider) {
    this.module = module;
    this.param1Provider = param1Provider;
    this.param2Provider = param2Provider;
    this.param3Provider = param3Provider;
  }

  @Override
  public String get() {
    return provideString(module, param1Provider.get(), param2Provider, param3Provider);
  }

  public static DaggerModule1_ProvideStringFactory create(DaggerModule1 module,
      Provider<String> param1Provider, Provider<CharSequence> param2Provider,
      Provider<List<String>> param3Provider) {
    return new DaggerModule1_ProvideStringFactory(module, param1Provider, param2Provider, param3Provider);
  }

  public static String provideString(DaggerModule1 instance, String param1,
      Provider<CharSequence> param2, Provider<List<String>> param3) {
    return Preconditions.checkNotNull(instance.provideString(param1, param2, param3), "Cannot return null from a non-@Nullable @Provides method");
  }
}
     */

    compile(
      """
        package test
        
        import dagger.Module
        import dagger.Provides
        import javax.inject.Named
        import javax.inject.Provider
        
        @Module
        class DaggerModule1 {
          @Provides fun provideString(
            @Named("abc") param1: String, 
            param2: Provider<CharSequence>, 
            param3: Provider<List<String>> 
          ): String = param1 + param2.get() + param3.get()[0]
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideString")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(
          daggerModule1, Provider::class.java, Provider::class.java, Provider::class.java
        )
        .inOrder()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val module = daggerModule1.newInstanceNoArgs()

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null, module, Provider { "a" }, Provider { "b" }, Provider { listOf("c") })
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideString" }
        .invoke(null, module, "a", Provider<CharSequence> { "b" }, Provider { listOf("c") })
        as String

      assertThat(providedString).isEqualTo("abc")
      assertThat((factoryInstance as Factory<String>).get()).isEqualTo("abc")
    }
  }

  @Test
  fun `a factory class is generated for a provider method with lazy parameters`() {
    /*
package test;

import dagger.Lazy;
import dagger.internal.DoubleCheck;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import java.util.List;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_ProvideStringFactory implements Factory<String> {
  private final DaggerModule1 module;

  private final Provider<String> param1Provider;

  private final Provider<CharSequence> param2Provider;

  private final Provider<List<String>> param3Provider;

  public DaggerModule1_ProvideStringFactory(DaggerModule1 module, Provider<String> param1Provider,
      Provider<CharSequence> param2Provider, Provider<List<String>> param3Provider) {
    this.module = module;
    this.param1Provider = param1Provider;
    this.param2Provider = param2Provider;
    this.param3Provider = param3Provider;
  }

  @Override
  public String get() {
    return provideString(module, param1Provider.get(), DoubleCheck.lazy(param2Provider), DoubleCheck.lazy(param3Provider));
  }

  public static DaggerModule1_ProvideStringFactory create(DaggerModule1 module,
      Provider<String> param1Provider, Provider<CharSequence> param2Provider,
      Provider<List<String>> param3Provider) {
    return new DaggerModule1_ProvideStringFactory(module, param1Provider, param2Provider, param3Provider);
  }

  public static String provideString(DaggerModule1 instance, String param1,
      Lazy<CharSequence> param2, Lazy<List<String>> param3) {
    return Preconditions.checkNotNull(instance.provideString(param1, param2, param3), "Cannot return null from a non-@Nullable @Provides method");
  }
}
     */

    compile(
      """
        package test
        
        import dagger.Lazy
        import dagger.Module
        import dagger.Provides
        import javax.inject.Named
        
        @Module
        class DaggerModule1 {
          @Provides fun provideString(
            @Named("abc") param1: String, 
            param2: Lazy<CharSequence>, 
            param3: Lazy<List<String>> 
          ): String = param1 + param2.get() + param3.get()[0]
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideString")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(
          daggerModule1, Provider::class.java, Provider::class.java, Provider::class.java
        )
        .inOrder()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val module = daggerModule1.newInstanceNoArgs()

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null, module, Provider { "a" }, Provider { "b" }, Provider { listOf("c") })
        as Factory<String>
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideString" }
        .invoke(null, module, "a", Lazy<CharSequence> { "b" }, Lazy { listOf("c") })
        as String

      assertThat(providedString).isEqualTo("abc")
      assertThat(factoryInstance.get()).isEqualTo("abc")
    }
  }

  @Test
  fun `a factory class is generated for a provider method with generic parameters`() {
    /*
package test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import java.util.List;
import javax.annotation.Generated;
import javax.inject.Provider;
import kotlin.Pair;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_ProvideStringFactory implements Factory<String> {
  private final DaggerModule1 module;

  private final Provider<List<String>> param1Provider;

  private final Provider<Pair<Pair<String, Integer>, ? extends List<String>>> param2Provider;

  public DaggerModule1_ProvideStringFactory(DaggerModule1 module,
      Provider<List<String>> param1Provider,
      Provider<Pair<Pair<String, Integer>, ? extends List<String>>> param2Provider) {
    this.module = module;
    this.param1Provider = param1Provider;
    this.param2Provider = param2Provider;
  }

  @Override
  public String get() {
    return provideString(module, param1Provider.get(), param2Provider.get());
  }

  public static DaggerModule1_ProvideStringFactory create(DaggerModule1 module,
      Provider<List<String>> param1Provider,
      Provider<Pair<Pair<String, Integer>, ? extends List<String>>> param2Provider) {
    return new DaggerModule1_ProvideStringFactory(module, param1Provider, param2Provider);
  }

  public static String provideString(DaggerModule1 instance, List<String> param1,
      Pair<Pair<String, Integer>, ? extends List<String>> param2) {
    return Preconditions.checkNotNull(instance.provideString(param1, param2), "Cannot return null from a non-@Nullable @Provides method");
  }
}
     */

    compile(
      """
        package test
        
        import dagger.Module
        import dagger.Provides
        import javax.inject.Named
        
        @Module
        class DaggerModule1 {
          @Provides fun provideString(
            @Named("abc") param1: List<String>, 
            param2: Pair<Pair<String, Int>, List<String>> 
          ): String = param1[0] + param2.first.first + param2.second[0]
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideString")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(daggerModule1, Provider::class.java, Provider::class.java)
        .inOrder()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val module = daggerModule1.newInstanceNoArgs()

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null, module, Provider { listOf("a") },
          Provider { Pair(Pair("b", 1), listOf("c")) }
        )
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideString" }
        .invoke(null, module, listOf("a"), Pair(Pair("b", 1), listOf("c"))) as String

      assertThat(providedString).isEqualTo("abc")
      assertThat((factoryInstance as Factory<String>).get()).isEqualTo("abc")
    }
  }

  @Test
  fun `a factory class is generated for a provider method with parameters in an object`() {
    /*
package test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_ProvideStringFactory implements Factory<String> {
  private final Provider<String> param1Provider;

  private final Provider<CharSequence> param2Provider;

  public DaggerModule1_ProvideStringFactory(Provider<String> param1Provider,
      Provider<CharSequence> param2Provider) {
    this.param1Provider = param1Provider;
    this.param2Provider = param2Provider;
  }

  @Override
  public String get() {
    return provideString(param1Provider.get(), param2Provider.get());
  }

  public static DaggerModule1_ProvideStringFactory create(Provider<String> param1Provider,
      Provider<CharSequence> param2Provider) {
    return new DaggerModule1_ProvideStringFactory(param1Provider, param2Provider);
  }

  public static String provideString(String param1, CharSequence param2) {
    return Preconditions.checkNotNull(DaggerModule1.INSTANCE.provideString(param1, param2), "Cannot return null from a non-@Nullable @Provides method");
  }
}
     */

    compile(
      """
        package test
        
        import dagger.Module
        import dagger.Provides
        import javax.inject.Named
        
        @Module
        object DaggerModule1 {
          @Provides fun provideString(
            @Named("abc") param1: String, 
            param2: CharSequence 
          ): String = param1 + param2
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideString")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(Provider::class.java, Provider::class.java)
        .inOrder()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null, Provider { "a" }, Provider<CharSequence> { "b" })
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideString" }
        .invoke(null, "a", "b" as CharSequence) as String

      assertThat(providedString).isEqualTo("ab")
      assertThat((factoryInstance as Factory<String>).get()).isEqualTo("ab")
    }
  }

  @Test
  fun `a factory class is generated for a provider method with parameters in a companion object`() {
    /*
package test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_Companion_ProvideStringFactory implements Factory<String> {
  private final Provider<String> param1Provider;

  private final Provider<CharSequence> param2Provider;

  public DaggerModule1_Companion_ProvideStringFactory(Provider<String> param1Provider,
      Provider<CharSequence> param2Provider) {
    this.param1Provider = param1Provider;
    this.param2Provider = param2Provider;
  }

  @Override
  public String get() {
    return provideString(param1Provider.get(), param2Provider.get());
  }

  public static DaggerModule1_Companion_ProvideStringFactory create(Provider<String> param1Provider,
      Provider<CharSequence> param2Provider) {
    return new DaggerModule1_Companion_ProvideStringFactory(param1Provider, param2Provider);
  }

  public static String provideString(String param1, CharSequence param2) {
    return Preconditions.checkNotNull(DaggerModule1.Companion.provideString(param1, param2), "Cannot return null from a non-@Nullable @Provides method");
  }
}
     */

    compile(
      """
        package test
        
        import dagger.Module
        import dagger.Provides
        import javax.inject.Named
        
        @Module
        abstract class DaggerModule1 {
          @dagger.Binds abstract fun bindString(string: String): CharSequence
          
          companion object {
            @Provides fun provideString(
              @Named("abc") param1: String, 
              param2: CharSequence 
            ): String = param1 + param2
          }
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideString", companion = true)

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(Provider::class.java, Provider::class.java)
        .inOrder()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null, Provider { "a" }, Provider<CharSequence> { "b" })
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideString" }
        .invoke(null, "a", "b" as CharSequence) as String

      assertThat(providedString).isEqualTo("ab")
      assertThat((factoryInstance as Factory<String>).get()).isEqualTo("ab")
    }
  }

  @Test
  fun `a factory class is generated for a nullable provider method`() {
    /*
package test;

import dagger.internal.Factory;
import javax.annotation.Generated;
import org.jetbrains.annotations.Nullable;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_ProvideStringFactory implements Factory<String> {
  private final DaggerModule1 module;

  public DaggerModule1_ProvideStringFactory(DaggerModule1 module) {
    this.module = module;
  }

  @Override
  @Nullable
  public String get() {
    return provideString(module);
  }

  public static DaggerModule1_ProvideStringFactory create(DaggerModule1 module) {
    return new DaggerModule1_ProvideStringFactory(module);
  }

  @Nullable
  public static String provideString(DaggerModule1 instance) {
    return instance.provideString();
  }
}
     */

    compile(
      """
        package test
        
        import dagger.Module
        import dagger.Provides
        import javax.inject.Named
        
        @Module
        class DaggerModule1 {
          @Provides fun provideString(): String? = null
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideString")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(daggerModule1)

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val module = daggerModule1.newInstanceNoArgs()

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null, module)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideString" }
        .invoke(null, module) as? String

      assertThat(providedString).isNull()
      assertThat((factoryInstance as Factory<String>).get()).isNull()
    }
  }

  @Test
  fun `a factory class is generated for a nullable provider method in an object`() {
    /*
package test;

import dagger.internal.Factory;
import javax.annotation.Generated;
import org.jetbrains.annotations.Nullable;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_ProvideStringFactory implements Factory<String> {
  @Override
  @Nullable
  public String get() {
    return provideString();
  }

  public static DaggerModule1_ProvideStringFactory create() {
    return InstanceHolder.INSTANCE;
  }

  @Nullable
  public static String provideString() {
    return DaggerModule1.INSTANCE.provideString();
  }

  private static final class InstanceHolder {
    private static final DaggerModule1_ProvideStringFactory INSTANCE = new DaggerModule1_ProvideStringFactory();
  }
}
     */

    compile(
      """
        package test
        
        import dagger.Module
        import dagger.Provides
        import javax.inject.Named
        
        @Module
        object DaggerModule1 {
          @Provides fun provideString(): String? = null
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideString")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideString" }
        .invoke(null) as? String

      assertThat(providedString).isNull()
      assertThat((factoryInstance as Factory<String>).get()).isNull()
    }
  }

  @Test
  fun `a factory class is generated for a nullable provider method with nullable parameters`() {
    /*
package test;

import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.jetbrains.annotations.Nullable;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_ProvideStringFactory implements Factory<String> {
  private final DaggerModule1 module;

  private final Provider<String> param1Provider;

  private final Provider<CharSequence> param2Provider;

  public DaggerModule1_ProvideStringFactory(DaggerModule1 module, Provider<String> param1Provider,
      Provider<CharSequence> param2Provider) {
    this.module = module;
    this.param1Provider = param1Provider;
    this.param2Provider = param2Provider;
  }

  @Override
  @Nullable
  public String get() {
    return provideString(module, param1Provider.get(), param2Provider.get());
  }

  public static DaggerModule1_ProvideStringFactory create(DaggerModule1 module,
      Provider<String> param1Provider, Provider<CharSequence> param2Provider) {
    return new DaggerModule1_ProvideStringFactory(module, param1Provider, param2Provider);
  }

  @Nullable
  public static String provideString(DaggerModule1 instance, String param1, CharSequence param2) {
    return instance.provideString(param1, param2);
  }
}
     */

    compile(
      """
        package test
        
        import dagger.Module
        import dagger.Provides
        import javax.inject.Named
        
        @Module
        class DaggerModule1 {
          @Provides fun provideString(
            @Named("abc") param1: String?, 
            param2: CharSequence? 
          ): String? {
            check(param1 == null)
            check(param2 == null)
            return null
          }
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideString")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(daggerModule1, Provider::class.java, Provider::class.java)
        .inOrder()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val module = daggerModule1.newInstanceNoArgs()

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null, module, Provider { null }, Provider { null })
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideString" }
        .invoke(null, module, null, null) as? String

      assertThat(providedString).isNull()
      assertThat((factoryInstance as Factory<String>).get()).isNull()
    }
  }

  @Test
  fun `no factory class is generated for a binding method in an abstract class`() {
    compile(
      """
        package test
        
        @dagger.Module
        abstract class DaggerModule1 {
          @dagger.Binds abstract fun bindString(string: String): CharSequence
        }
        """
    ) {
      if (useDagger) {
        assertThat(sourcesGeneratedByAnnotationProcessor).isEmpty()
      }
    }
  }

  @Test
  fun `a factory class is generated for a provider method in a companion object`() {
    /*
package test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://dagger.dev"
)
@SuppressWarnings({
  "unchecked",
  "rawtypes"
})
public final class DaggerModule1_Companion_ProvideStringFactory implements Factory<String> {
  @Override
  public String get() {
    return provideString();
  }

  public static DaggerModule1_Companion_ProvideStringFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static String provideString() {
    return Preconditions.checkNotNull(DaggerModule1.Companion.provideString(), "Cannot return null from a non-@Nullable @Provides method");
  }

  private static final class InstanceHolder {
    private static final DaggerModule1_Companion_ProvideStringFactory INSTANCE = new DaggerModule1_Companion_ProvideStringFactory();
  }
}
     */

    compile(
      """
        package test
        
        @dagger.Module
        abstract class DaggerModule1 {
          @dagger.Binds abstract fun bindString(string: String): CharSequence
          
          companion object {
            @dagger.Provides fun provideString(): String = "abc"          
          }
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideString", companion = true)

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideString" }
        .invoke(null) as String

      assertThat(providedString).isEqualTo("abc")
      assertThat((factoryInstance as Factory<String>).get()).isEqualTo("abc")
    }
  }

  @Test
  fun `a factory class is generated for a provider method in an inner module`() {
    /*
package test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://dagger.dev"
)
@SuppressWarnings({
  "unchecked",
  "rawtypes"
})
public final class ComponentInterface_InnerModule_ProvideStringFactory implements Factory<String> {
  @Override
  public String get() {
    return provideString();
  }

  public static ComponentInterface_InnerModule_ProvideStringFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static String provideString() {
    return Preconditions.checkNotNull(ComponentInterface.InnerModule.INSTANCE.provideString(), "Cannot return null from a non-@Nullable @Provides method");
  }

  private static final class InstanceHolder {
    private static final ComponentInterface_InnerModule_ProvideStringFactory INSTANCE = new ComponentInterface_InnerModule_ProvideStringFactory();
  }
}
     */

    compile(
      """
        package test
        
        interface ComponentInterface {
          @dagger.Module
          object InnerModule {
            @dagger.Provides fun provideString(): String = "abc"          
          }
        }
        """
    ) {
      val factoryClass = innerModule.moduleFactoryClass("provideString")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideString" }
        .invoke(null) as String

      assertThat(providedString).isEqualTo("abc")
      assertThat((factoryInstance as Factory<String>).get()).isEqualTo("abc")
    }
  }

  @Test
  fun `a factory class is generated for a provider method returning an inner class`() {
    compile(
      """
        package test
        
        import dagger.Module
        import dagger.Provides
        import dev.zacsweers.forge.factory.OuterClass
        
        @Module
        object DaggerModule1 {
          @Provides fun provideInnerClass(): OuterClass.InnerClass = OuterClass.InnerClass()
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideInnerClass")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()
    }
  }

  @Test
  fun `a factory class is generated for a provider method in a companion object in an inner module`() { // ktlint-disable max-line-length
    /*
package test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://dagger.dev"
)
@SuppressWarnings({
  "unchecked",
  "rawtypes"
})
public final class ComponentInterface_InnerModule_Companion_ProvideStringFactory implements Factory<String> {
  @Override
  public String get() {
    return provideString();
  }

  public static ComponentInterface_InnerModule_Companion_ProvideStringFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static String provideString() {
    return Preconditions.checkNotNull(ComponentInterface.InnerModule.Companion.provideString(), "Cannot return null from a non-@Nullable @Provides method");
  }

  private static final class InstanceHolder {
    private static final ComponentInterface_InnerModule_Companion_ProvideStringFactory INSTANCE = new ComponentInterface_InnerModule_Companion_ProvideStringFactory();
  }
}
     */

    compile(
      """
        package test
        
        interface ComponentInterface {
          @dagger.Module
          abstract class InnerModule {
            @dagger.Binds abstract fun bindString(string: String): CharSequence
            
            companion object {
              @dagger.Provides fun provideString(): String = "abc"          
            }
          }
        }
        """
    ) {
      val factoryClass = innerModule.moduleFactoryClass("provideString", companion = true)

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideString" }
        .invoke(null) as String

      assertThat(providedString).isEqualTo("abc")
      assertThat((factoryInstance as Factory<String>).get()).isEqualTo("abc")
    }
  }

  @Test
  fun `no factory class is generated for multibindings`() {
    compile(
      """
        package test
        
        @dagger.Module
        abstract class DaggerModule1 {
          @dagger.Binds @dagger.multibindings.IntoSet abstract fun bindString(string: String): CharSequence
        }
        """
    ) {
      if (useDagger) {
        assertThat(sourcesGeneratedByAnnotationProcessor).isEmpty()
      }
    }
  }

  @Test
  fun `a factory class is generated for multibindings provider method`() {
    /*
package test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://dagger.dev"
)
@SuppressWarnings({
  "unchecked",
  "rawtypes"
})
public final class DaggerModule1_ProvideStringFactory implements Factory<String> {
  @Override
  public String get() {
    return provideString();
  }

  public static DaggerModule1_ProvideStringFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static String provideString() {
    return Preconditions.checkNotNull(DaggerModule1.INSTANCE.provideString(), "Cannot return null from a non-@Nullable @Provides method");
  }

  private static final class InstanceHolder {
    private static final DaggerModule1_ProvideStringFactory INSTANCE = new DaggerModule1_ProvideStringFactory();
  }
}
     */

    compile(
      """
        package test
        
        @dagger.Module
        object DaggerModule1 {
          @dagger.Provides @dagger.multibindings.IntoSet fun provideString(): String = "abc"
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideString")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideString" }
        .invoke(null) as String

      assertThat(providedString).isEqualTo("abc")
      assertThat((factoryInstance as Factory<String>).get()).isEqualTo("abc")
    }
  }

  @Test
  fun `a factory class is generated for multibindings provider method elements into set`() {
    /*
package test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import java.util.Set;
import javax.annotation.Generated;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://dagger.dev"
)
@SuppressWarnings({
  "unchecked",
  "rawtypes"
})
public final class DaggerModule1_ProvideStringFactory implements Factory<Set<String>> {
  @Override
  public Set<String> get() {
    return provideString();
  }

  public static DaggerModule1_ProvideStringFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static Set<String> provideString() {
    return Preconditions.checkNotNull(DaggerModule1.INSTANCE.provideString(), "Cannot return null from a non-@Nullable @Provides method");
  }

  private static final class InstanceHolder {
    private static final DaggerModule1_ProvideStringFactory INSTANCE = new DaggerModule1_ProvideStringFactory();
  }
}
     */

    compile(
      """
        package test
        
        @dagger.Module
        object DaggerModule1 {
          @dagger.Provides @dagger.multibindings.ElementsIntoSet fun provideString(): Set<String> = setOf("abc")
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideString")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedStringSet = staticMethods.single { it.name == "provideString" }
        .invoke(null) as Set<String>

      assertThat(providedStringSet).containsExactly("abc")
      assertThat((factoryInstance as Factory<Set<String>>).get()).containsExactly("abc")
    }
  }

  @Test
  fun `a factory class is generated for a provided function`() {
    /*
package test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;
import kotlin.jvm.functions.Function1;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_ProvideFunctionFactory implements Factory<Function1<String, Integer>> {
  @Override
  public Function1<String, Integer> get() {
    return provideFunction();
  }

  public static DaggerModule1_ProvideFunctionFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static Function1<String, Integer> provideFunction() {
    return Preconditions.checkNotNull(DaggerModule1.INSTANCE.provideFunction(), "Cannot return null from a non-@Nullable @Provides method");
  }

  private static final class InstanceHolder {
    private static final DaggerModule1_ProvideFunctionFactory INSTANCE = new DaggerModule1_ProvideFunctionFactory();
  }
}
     */

    compile(
      """
        package test
        
        import dagger.Module
        import dagger.Provides
        
        @Module
        object DaggerModule1 {
          @Provides fun provideFunction(): (String) -> Int {
            return { string -> string.length }
          }
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideFunction")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null)
        as Factory<(String) -> Int>
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedInt = staticMethods.single { it.name == "provideFunction" }
        .invoke(null) as (String) -> Int

      assertThat(providedInt.invoke("abc")).isEqualTo(3)
      assertThat(factoryInstance.get().invoke("abcd")).isEqualTo(4)
    }
  }

  @Test
  fun `a factory class is generated for a multibindings provided function with a typealias`() {
    /*
package test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import java.util.List;
import java.util.Set;
import javax.annotation.Generated;
import javax.inject.Provider;
import kotlin.jvm.functions.Function1;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_ProvideFunctionFactory implements Factory<Set<Function1<List<String>, List<String>>>> {
  private final Provider<String> stringProvider;

  public DaggerModule1_ProvideFunctionFactory(Provider<String> stringProvider) {
    this.stringProvider = stringProvider;
  }

  @Override
  public Set<Function1<List<String>, List<String>>> get() {
    return provideFunction(stringProvider.get());
  }

  public static DaggerModule1_ProvideFunctionFactory create(Provider<String> stringProvider) {
    return new DaggerModule1_ProvideFunctionFactory(stringProvider);
  }

  public static Set<Function1<List<String>, List<String>>> provideFunction(String string) {
    return Preconditions.checkNotNull(DaggerModule1.INSTANCE.provideFunction(string), "Cannot return null from a non-@Nullable @Provides method");
  }
}
     */

    compile(
      """
        package test
        
        import dagger.Module
        import dagger.Provides
        import dagger.multibindings.ElementsIntoSet
        
        typealias StringList = List<String>
        
        @Module
        object DaggerModule1 {
          @Provides @ElementsIntoSet fun provideFunction(
            string: String
          ): @JvmSuppressWildcards Set<(StringList) -> StringList> {
            return setOf { list -> listOf(string) }
          }
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideFunction")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null, Provider { "abc" })
        as Factory<Set<(List<String>) -> List<String>>>
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedStringSet = staticMethods.single { it.name == "provideFunction" }
        .invoke(null, "abc") as Set<(List<String>) -> List<String>>

      assertThat(providedStringSet.single().invoke(emptyList())).containsExactly("abc")
      assertThat(factoryInstance.get().single().invoke(emptyList())).containsExactly("abc")
    }
  }

  @Test
  fun `an error is thrown for overloaded provider methods`() {
    compile(
      """
        package test
        
        import dagger.Module
        import dagger.Provides
        import dagger.multibindings.ElementsIntoSet
        
        typealias StringList = List<String>
        
        @Module
        object DaggerModule1 {
            @Provides fun provideString(): String = ""
            @Provides fun provideString(s: String): Int = 1
        }
        """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      assertThat(messages).contains(
        "Cannot have more than one binding method with the same name in a single module"
      )
    }
  }

  @Test
  fun `a factory class is generated for a method returning a java class with a star import`() {
    compile(
      """
        package test
        
        import dagger.Module
        import dagger.Provides
        
        @Module
        object DaggerModule1 {
          @Provides fun provideClass(): Class<*> = java.lang.Runnable::class.java
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideClass")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()
    }
  }

  @Test
  fun `a factory class is generated for a method returning a class with a named import`() {
    compile(
      """
        package test
        
        import dagger.Module
        import dagger.Provides
        import java.lang.Runnable as NamedRunnable
        
        @Module
        object DaggerModule1 {
          @Provides fun provideRunner(): NamedRunnable = NamedRunnable {}
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideRunner")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()
    }
  }

  @Test
  fun `a factory class is generated ignoring the named import original path`() {
    compile(
      """
        package test
        
        import dagger.Module
        import dagger.Provides
        import java.util.*
        import dev.zacsweers.forge.factory.Date as AnotherDate

        @Module
        object DaggerModule1 {
          @Provides fun provideDate(): Date = Date(1000)
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideDate")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null) as Factory<Any>
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedContributingObject = staticMethods
        .single { it.name == "provideDate" }
        .invoke(null)

      assertThat(providedContributingObject).isInstanceOf(java.util.Date::class.java)
    }
  }

  @Test
  fun `a return type for a provider method is required`() {
    compile(
      """
        package test
        
        @dagger.Module
        class DaggerModule1 {
          @dagger.Provides fun provideString() = "abc"
        }
        """
    ) {
      assumeFalse(useDagger)

      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      assertThat(messages).contains("Source.kt: (5, 3)")
      assertThat(messages).contains(
        "Dagger provider methods must specify the return type explicitly when using Anvil. " +
          "The return type cannot be inferred implicitly."
      )
    }
  }

  @Test
  fun `a factory class is generated for a capital case package name`() {
    compile(
      """
        package test
        
        import dagger.Module
        import dagger.Provides
        import dev.zacsweers.forge.factory.UppercasePackage.TestClassInUppercasePackage
        
        @Module
        object DaggerModule1 {
          @Provides fun provideThing(): TestClassInUppercasePackage = TestClassInUppercasePackage()
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideThing")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val thingProvider = staticMethods.single { it.name == "provideThing" }
      assertThat(thingProvider.invoke(null)).isInstanceOf(TestClassInUppercasePackage::class.java)
    }
  }

  @Test
  fun `a factory class is generated for a capital case package name and lower class name`() {
    compile(
      """
        package test
        
        import dagger.Module
        import dagger.Provides
        import dev.zacsweers.forge.factory.UppercasePackage.lowerCaseClassInUppercasePackage
        
        @Module
        object DaggerModule1 {
          @Provides fun provideThing(): lowerCaseClassInUppercasePackage {
            return lowerCaseClassInUppercasePackage()
          }
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideThing")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val thingProvider = staticMethods.single { it.name == "provideThing" }
      assertThat(thingProvider.invoke(null))
        .isInstanceOf(lowerCaseClassInUppercasePackage::class.java)
    }
  }

  @Test
  fun `a factory class is generated for a capital case package name and inner class`() {
    compile(
      """
        package test
        
        import dagger.Module
        import dagger.Provides
        import dev.zacsweers.forge.factory.UppercasePackage.OuterClass.InnerClass
        
        @Module
        object DaggerModule1 {
          @Provides fun provideThing(): InnerClass = InnerClass()
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideThing")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val thingProvider = staticMethods.single { it.name == "provideThing" }
      assertThat(thingProvider.invoke(null)).isInstanceOf(OuterClass.InnerClass::class.java)
    }
  }

  @Test
  fun `a factory class is generated for an uppercase factory function`() {
    compile(
      """
        package test.a
        
        import dev.zacsweers.forge.factory.test.b.User
        
        fun User(): User = User(42)
        """,
      """
        package test.b
        
        data class User(val age: Int)          
        """,
      """
        package test
        
        import dev.zacsweers.forge.factory.test.a.User
        import dev.zacsweers.forge.factory.test.b.User
        import dagger.Module
        import dagger.Provides
        
        @Module
        object DaggerModule1 {
          @Provides fun user(): User = User()
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("user")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val userProvider = staticMethods.single { it.name == "user" }
      assertThat(userProvider.invoke(null)).isNotNull()
    }
  }

  @Test
  fun `a factory class is generated for provided properties`() {
    /*
package test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.processing.Generated;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_GetStringFactory implements Factory<String> {
  private final DaggerModule1 module;

  public DaggerModule1_GetStringFactory(DaggerModule1 module) {
    this.module = module;
  }

  @Override
  public String get() {
    return getString(module);
  }

  public static DaggerModule1_GetStringFactory create(DaggerModule1 module) {
    return new DaggerModule1_GetStringFactory(module);
  }

  public static String getString(DaggerModule1 instance) {
    return Preconditions.checkNotNull(instance.getString(), "Cannot return null from a non-@Nullable @Provides method");
  }
}
     */
    compile(
      """
        package test
        
        import dagger.Module
        import dagger.Provides
        
        @Module
        class DaggerModule1 {
          @get:Provides val string: String = "abc"
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("getString")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(daggerModule1)

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val module = daggerModule1.newInstanceNoArgs()

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null, module)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "getString" }
        .invoke(null, module) as String

      assertThat(providedString).isEqualTo("abc")
      assertThat((factoryInstance as Factory<String>).get()).isEqualTo("abc")
    }
  }

  @Test
  fun `a factory class is generated for provided properties in an object module`() {
    /*
package test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.processing.Generated;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_GetStringFactory implements Factory<String> {
  @Override
  public String get() {
    return getString();
  }

  public static DaggerModule1_GetStringFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static String getString() {
    return Preconditions.checkNotNull(DaggerModule1.INSTANCE.getString(), "Cannot return null from a non-@Nullable @Provides method");
  }

  private static final class InstanceHolder {
    private static final DaggerModule1_GetStringFactory INSTANCE = new DaggerModule1_GetStringFactory();
  }
}
     */
    compile(
      """
        package test
        
        import dagger.Module
        import dagger.Provides
        
        @Module
        object DaggerModule1 {
          @get:Provides val string: String = "abc"
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("getString")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "getString" }
        .invoke(null) as String

      assertThat(providedString).isEqualTo("abc")
      assertThat((factoryInstance as Factory<String>).get()).isEqualTo("abc")
    }
  }

  @Test
  fun `a factory class is generated for provided properties in a companion object module`() {
    /*
package test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.processing.Generated;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_GetStringFactory implements Factory<String> {
  @Override
  public String get() {
    return getString();
  }

  public static DaggerModule1_GetStringFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static String getString() {
    return Preconditions.checkNotNull(DaggerModule1.INSTANCE.getString(), "Cannot return null from a non-@Nullable @Provides method");
  }

  private static final class InstanceHolder {
    private static final DaggerModule1_GetStringFactory INSTANCE = new DaggerModule1_GetStringFactory();
  }
}
     */
    compile(
      """
        package test
        
        import dagger.Binds
        import dagger.Module
        import dagger.Provides
        
        @Module
        abstract class DaggerModule1 {
          @Binds abstract fun bindString(string: String): CharSequence
          
          companion object {
            @get:Provides val string: String = "abc"
          }
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("getString", companion = true)

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "getString" }
        .invoke(null) as String

      assertThat(providedString).isEqualTo("abc")
      assertThat((factoryInstance as Factory<String>).get()).isEqualTo("abc")
    }
  }

  @Test
  fun `a factory class is generated for provided nullable properties`() {
    /*
package test;

import dagger.internal.Factory;
import javax.annotation.processing.Generated;
import org.jetbrains.annotations.Nullable;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_GetStringFactory implements Factory<String> {
  private final DaggerModule1 module;

  public DaggerModule1_GetStringFactory(DaggerModule1 module) {
    this.module = module;
  }

  @Override
  @Nullable
  public String get() {
    return getString(module);
  }

  public static DaggerModule1_GetStringFactory create(DaggerModule1 module) {
    return new DaggerModule1_GetStringFactory(module);
  }

  @Nullable
  public static String getString(DaggerModule1 instance) {
    return instance.getString();
  }
}
     */
    compile(
      """
        package test
        
        import dagger.Module
        import dagger.Provides
        
        @Module
        class DaggerModule1 {
          @get:Provides val string: String? = null
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("getString")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(daggerModule1)

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val module = daggerModule1.newInstanceNoArgs()

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null, module)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "getString" }
        .invoke(null, module) as String?

      assertThat(providedString).isNull()
      assertThat((factoryInstance as Factory<String?>).get()).isNull()
    }
  }

  @Test
  fun `a factory class is generated for provided nullable properties in an object module`() {
    /*
package test;

import dagger.internal.Factory;
import javax.annotation.processing.Generated;
import org.jetbrains.annotations.Nullable;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_GetStringFactory implements Factory<String> {
  @Override
  @Nullable
  public String get() {
    return getString();
  }

  public static DaggerModule1_GetStringFactory create() {
    return InstanceHolder.INSTANCE;
  }

  @Nullable
  public static String getString() {
    return DaggerModule1.INSTANCE.getString();
  }

  private static final class InstanceHolder {
    private static final DaggerModule1_GetStringFactory INSTANCE = new DaggerModule1_GetStringFactory();
  }
}
     */
    compile(
      """
        package test
        
        import dagger.Module
        import dagger.Provides
        
        @Module
        object DaggerModule1 {
          @get:Provides val string: String? = null
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("getString")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "getString" }
        .invoke(null) as String?

      assertThat(providedString).isNull()
      assertThat((factoryInstance as Factory<String?>).get()).isNull()
    }
  }

  @Suppress("CHANGING_ARGUMENTS_EXECUTION_ORDER_FOR_NAMED_VARARGS")
  private fun compile(
    vararg inputSources: String,
    block: KotlinCompilation.Result.() -> Unit = { }
  ): KotlinCompilation.Result {
    val sourceFiles = inputSources.map { content ->
      val packageDir = content.lines()
        .first { it.trim().startsWith("package ") }
        .substringAfter("package ")
        .replace('.', '/')

      val name = "${temporaryFolder.root.absolutePath}/sources/src/main/java/$packageDir/Source.kt"
      with(File(name).parentFile) {
        check(exists() || mkdirs())
      }

      kotlin(name, contents = content, trimIndent = true)
    }

    val generatedFiles: Sequence<SourceFile>
    if (!useDagger) {
      val compilation = KotlinCompilation()
        .apply {
          workingDir = temporaryFolder.root
          inheritClassPath = true
          symbolProcessors = listOf(ForgeProcessor())
          sources = sourceFiles
          verbose = false
        }
      val result = compilation.compile()
      check(result.exitCode == OK) {
        "Failed second compilation with $result"
      }

      generatedFiles = compilation.kspSourcesDir.walkTopDown().filter { it.extension == "kt" }
        .map { SourceFile.fromPath(it) }
    } else {
      generatedFiles = emptySequence()
    }

    // Second compilation required until https://github.com/tschuchortdev/kotlin-compile-testing/issues/72
    val secondCompilation = KotlinCompilation()
      .apply {
        workingDir = temporaryFolder.root
        inheritClassPath = true
        sources = sourceFiles + generatedFiles
        verbose = false
        if (useDagger) {
          annotationProcessors = listOf(ComponentProcessor())
        }
      }
    val secondResult = secondCompilation.compile()
    check(secondResult.exitCode == OK) {
      "Failed second compilation with $secondResult. Printing generated files:${generatedFiles.joinToString("\n\n") { it.toString() }}"
    }
    try {
      secondResult.block()
    } catch (e: Exception) {
      check(generatedFiles.toList().isNotEmpty()) {
        "No source files were generated!"
      }
      System.err.println("Failed block exec. Printing generated files:${generatedFiles.joinToString("\n\n") { it.toString() }}")
      throw e
    }
    return secondResult
  }
}