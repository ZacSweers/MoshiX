# Keep @JsonAdapter annotations at runtime
-keep interface dev.zacsweers.moshix.adapters.AdaptedBy { *; }

# Keep empty default constructors of Moshi adapter types in case they're referenced by @AdaptedBy annotations
-if public class * extends com.squareup.moshi.JsonAdapter
-keepclassmembers class <1> {
  public <init>();
}
-if public class * implements com.squareup.moshi.JsonAdapter.Factory
-keepclassmembers class <1> {
  public <init>();
}