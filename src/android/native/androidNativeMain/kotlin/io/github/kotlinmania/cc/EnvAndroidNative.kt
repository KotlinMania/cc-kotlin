@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.kotlinmania.cc

import kotlinx.cinterop.toKString
import platform.posix.getenv

internal actual fun getEnv(name: String): String? = getenv(name)?.toKString()
