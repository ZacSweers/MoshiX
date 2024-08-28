package dev.zacsweers.moshix.ir.gradle

import java.util.Properties
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

internal fun Project.createPropertiesProvider(filePath: String): Provider<Properties> {
  return project.providers.of(LocalProperties::class.java) {
    it.parameters.propertiesFile.set(project.layout.projectDirectory.file(filePath))
  }
}

/** Implementation of provider holding a local properties file's parsed [Properties]. */
internal abstract class LocalProperties : ValueSource<Properties, LocalProperties.Parameters> {
  interface Parameters : ValueSourceParameters {
    val propertiesFile: RegularFileProperty
  }

  override fun obtain(): Properties? {
    val provider = parameters.propertiesFile
    if (!provider.isPresent) {
      return null
    }
    val propertiesFile = provider.asFile.get()
    if (!propertiesFile.exists()) {
      return null
    }
    return Properties().apply { propertiesFile.inputStream().use(::load) }
  }
}
