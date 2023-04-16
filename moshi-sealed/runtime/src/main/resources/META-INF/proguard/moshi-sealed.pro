# Keep NestedSealed for runtime use.
-keep interface dev.zacsweers.moshix.sealed.annotations.NestedSealed { *; }

# Keep signatures for typed annotated with NestedSealed. This ensures that the annotation, while kept generally above,
# isn't stripped from the use on the class.
-keepnames @dev.zacsweers.moshix.sealed.annotations.NestedSealed class **

# Keep generic signatures and annotations at runtime.
# R8 requires InnerClasses and EnclosingMethod if you keepattributes Signature.
-keepattributes InnerClasses,Signature,RuntimeVisible*Annotations,EnclosingMethod
