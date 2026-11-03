package io.github.kotlinmania.cc

internal expect fun isWindows(): Boolean

internal expect fun exeExtension(): String

internal expect fun fileExists(path: String): Boolean
