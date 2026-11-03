@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.kotlinmania.cc

import platform.posix.access

internal actual fun isWindows(): Boolean = true

internal actual fun exeExtension(): String = ".exe"

internal actual fun fileExists(path: String): Boolean = access(path, 0) == 0
