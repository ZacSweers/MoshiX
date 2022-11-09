# Keep moshi-sealed annotations for runtime use
-keep interface dev.zacsweers.moshix.sealed.annotations.DefaultNull { *; }
-keep interface dev.zacsweers.moshix.sealed.annotations.DefaultObject { *; }
-keep interface dev.zacsweers.moshix.sealed.annotations.TypeLabel { *; }

# Keep JsonClass annotations for NestedSealed.Factory
-keep @com.squareup.moshi.JsonClass class *
-keep @com.squareup.moshi.JsonClass interface *
