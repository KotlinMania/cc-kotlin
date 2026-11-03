@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package io.github.kotlinmania.cc

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Foundation.writeToFile

internal actual fun appleWriteBytes(path: String, bytes: ByteArray): Result<Unit> =
    runCatching {
        val data =
            if (bytes.isEmpty()) {
                NSData()
            } else {
                bytes.usePinned { pinned ->
                    NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong().convert())
                }
            }
        val success = data.writeToFile(path, atomically = true)
        if (!success) {
            throw IllegalStateException("Failed to write data to $path")
        }
    }
