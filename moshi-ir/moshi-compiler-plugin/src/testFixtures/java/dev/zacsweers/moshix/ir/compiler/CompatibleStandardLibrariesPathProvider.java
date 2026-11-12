// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.moshix.ir.compiler;

import java.io.File;
import org.jetbrains.kotlin.test.services.KotlinStandardLibrariesPathProvider;

/** Declares the optional scripting lookup across supported Kotlin test-framework versions. */
public interface CompatibleStandardLibrariesPathProvider
    extends KotlinStandardLibrariesPathProvider {
  File scriptingCommonJarForTests();
}
