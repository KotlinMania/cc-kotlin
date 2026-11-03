@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.kotlinmania.cc

import platform.Foundation.NSFileManager

internal expect fun appleWriteBytes(path: String, bytes: ByteArray): Result<Unit>

internal class AppleFileHandle(
    private val path: String,
) : FileHandle {
    private val buffer = mutableListOf<Byte>()

    override fun write(bytes: ByteArray) {
        for (b in bytes) {
            buffer.add(b)
        }
        appleWriteBytes(path, buffer.toByteArray())
    }

    override fun flush() {}

    override fun sync() {}

    override fun close() {}
}

internal actual fun createNamed(path: String): Result<FileHandle> {
    val manager = NSFileManager.defaultManager
    if (manager.fileExistsAtPath(path)) {
        return Result.failure(FileAlreadyExistsException("File already exists: $path"))
    }
    val created = manager.createFileAtPath(path, null, null)
    if (!created) {
        return Result.failure(IllegalStateException("Failed to create file at $path"))
    }
    return Result.success(AppleFileHandle(path))
}

internal actual fun removeFile(path: String): Result<Unit> =
    runCatching {
        val manager = NSFileManager.defaultManager
        if (manager.fileExistsAtPath(path)) {
            if (!manager.removeItemAtPath(path, null)) {
                throw IllegalStateException("Failed to remove file at $path")
            }
        }
    }
