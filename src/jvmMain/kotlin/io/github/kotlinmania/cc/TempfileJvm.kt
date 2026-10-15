package io.github.kotlinmania.cc

import java.io.File
import java.io.RandomAccessFile

internal class JvmFileHandle(
    private val raf: RandomAccessFile,
) : FileHandle {
    override fun write(bytes: ByteArray) {
        raf.write(bytes)
    }

    override fun flush() {
        // RandomAccessFile writes unbuffered to the underlying file descriptor
    }

    override fun sync() {
        raf.fd.sync()
    }

    override fun close() {
        raf.close()
    }
}

internal actual fun createNamed(path: String): Result<FileHandle> =
    runCatching {
        val file = File(path)
        file.parentFile?.mkdirs()
        if (!file.createNewFile()) {
            throw FileAlreadyExistsException("File already exists: $path")
        }
        JvmFileHandle(RandomAccessFile(file, "rw"))
    }

internal actual fun removeFile(path: String): Result<Unit> =
    runCatching {
        val file = File(path)
        if (file.exists() && !file.delete()) {
            throw IllegalStateException("Failed to delete file: $path")
        }
    }
