@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.kotlinmania.cc

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.posix.EEXIST
import platform.posix.O_CREAT
import platform.posix.O_EXCL
import platform.posix.O_RDWR
import platform.posix.close
import platform.posix.errno
import platform.posix.fsync
import platform.posix.open
import platform.posix.remove
import platform.posix.write

internal class LinuxFileHandle(
    private var fd: Int,
) : FileHandle {
    override fun write(bytes: ByteArray) {
        if (fd < 0 || bytes.isEmpty()) return
        bytes.usePinned { pinned ->
            var written = 0
            while (written < bytes.size) {
                val n = write(fd, pinned.addressOf(written), (bytes.size - written).convert())
                if (n <= 0) break
                written += n.toInt()
            }
        }
    }

    override fun flush() {}

    override fun sync() {
        if (fd >= 0) {
            fsync(fd)
        }
    }

    override fun close() {
        if (fd >= 0) {
            close(fd)
            fd = -1
        }
    }
}

internal actual fun createNamed(path: String): Result<FileHandle> {
    val fd = open(path, O_RDWR or O_CREAT or O_EXCL, 384) // 384 = 0600 octal
    if (fd >= 0) {
        return Result.success(LinuxFileHandle(fd))
    }
    if (errno == EEXIST) {
        return Result.failure(FileAlreadyExistsException("File already exists: $path"))
    }
    return Result.failure(IllegalStateException("Failed to open file at $path: errno=$errno"))
}

internal actual fun removeFile(path: String): Result<Unit> {
    if (remove(path) == 0) {
        return Result.success(Unit)
    }
    return Result.failure(IllegalStateException("Failed to remove file at $path: errno=$errno"))
}
