package dev.zacsweers.moshix.ksp;

import com.squareup.moshi.JsonClass;

// Ensure that java classes handled by another generator are skipped
@JsonClass(generateAdapter = true, generator = "somethingelse")
public class JavaClassThatShouldBeSkipped {
}
