// RUN_PIPELINE_TILL: FIR2IR
// DISABLE_GENERATED_FIR_TAGS
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

// FILE: com/squareup/moshi/kotlin/codegen/JavaSuperclass.java
package com.squareup.moshi.kotlin.codegen;

public class JavaSuperclass {
  public int a = 1;
}

// FILE: source.kt
package test

import com.squareup.moshi.JsonClass
import com.squareup.moshi.kotlin.codegen.JavaSuperclass

<!MOSHI_ERROR!>@JsonClass(generateAdapter = true)
class ExtendsJavaType(var b: Int) : JavaSuperclass()<!>

