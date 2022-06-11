# Keep @JsonAdapter annotations at runtime
-keep interface dev.zacsweers.moshix.adapters.AdaptedBy { *; }
# We need to keep classes annotated with AdaptedBy in order to prevent it being stripped at runtime
# Unfortunately there is no way to say, -keepannotations @dev.zacsweers.moshix.adapters.AdaptedBy class *
# https://stackoverflow.com/a/69523469
-keep @dev.zacsweers.moshix.adapters.AdaptedBy class *

# Keep empty default constructors of Moshi adapter types in case they're referenced by @AdaptedBy annotations
-if public class * extends com.squareup.moshi.JsonAdapter
-keepclassmembers class <1> {
  public <init>();
}
-if public class * implements com.squareup.moshi.JsonAdapter.Factory
-keepclassmembers class <1> {
  public <init>();
}
