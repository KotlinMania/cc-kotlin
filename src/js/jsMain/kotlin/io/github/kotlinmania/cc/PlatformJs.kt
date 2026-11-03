package io.github.kotlinmania.cc

internal actual fun isWindows(): Boolean = false

internal actual fun exeExtension(): String = ""

internal actual fun fileExists(path: String): Boolean = false
