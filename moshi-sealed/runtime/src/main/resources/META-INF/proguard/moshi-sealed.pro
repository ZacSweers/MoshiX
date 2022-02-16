# Keep NestedSealed for runtime use
-keep interface dev.zacsweers.moshix.sealed.annotations.NestedSealed { *; }

# Keep generic signatures and annotations at runtime.
# R8 requires InnerClasses and EnclosingMethod if you keepattributes Signature.
-keepattributes InnerClasses,Signature,RuntimeVisible*Annotations,EnclosingMethod
