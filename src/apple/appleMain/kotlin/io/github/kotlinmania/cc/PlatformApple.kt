package io.github.kotlinmania.cc

import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory

internal actual fun isWindows(): Boolean = false

internal actual fun exeExtension(): String = ""

internal actual fun fileExists(path: String): Boolean =
    NSFileManager.defaultManager.fileExistsAtPath(path)

internal actual fun tempDir(): String = NSTemporaryDirectory().trimEnd('/').ifEmpty { "." }
