package io.github.kotlinmania.cc

import platform.Foundation.NSFileManager

internal actual fun isWindows(): Boolean = false

internal actual fun exeExtension(): String = ""

internal actual fun fileExists(path: String): Boolean =
    NSFileManager.defaultManager.fileExistsAtPath(path)
