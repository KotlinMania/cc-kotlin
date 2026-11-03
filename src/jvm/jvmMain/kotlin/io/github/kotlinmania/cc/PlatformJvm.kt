package io.github.kotlinmania.cc

import java.io.File

internal actual fun isWindows(): Boolean =
    System.getProperty("os.name")?.lowercase()?.contains("windows") == true

internal actual fun exeExtension(): String = if (isWindows()) ".exe" else ""

internal actual fun fileExists(path: String): Boolean = File(path).exists()
