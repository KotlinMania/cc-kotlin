package io.github.kotlinmania.cc

import java.io.File

internal actual fun isWindows(): Boolean = false

internal actual fun exeExtension(): String = ""

internal actual fun fileExists(path: String): Boolean = File(path).exists()
