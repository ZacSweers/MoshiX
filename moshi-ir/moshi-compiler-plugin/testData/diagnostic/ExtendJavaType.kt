// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
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
