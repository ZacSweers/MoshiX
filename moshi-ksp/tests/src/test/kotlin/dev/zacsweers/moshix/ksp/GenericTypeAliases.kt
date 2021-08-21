package dev.zacsweers.moshix.ksp

import com.squareup.moshi.JsonClass

typealias AnimalMap<T> = Map<Int, T>

// Regression test for https://github.com/ZacSweers/MoshiX/issues/127
@JsonClass(generateAdapter = true)
data class ApiResponse(
  val animalNames: AnimalMap<String>,
)