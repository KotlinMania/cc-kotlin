@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.kotlinmania.cc

import platform.posix.F_OK
import platform.posix.access

internal actual fun isWindows(): Boolean = false

internal actual fun exeExtension(): String = ""

internal actual fun fileExists(path: String): Boolean = access(path, F_OK) == 0
