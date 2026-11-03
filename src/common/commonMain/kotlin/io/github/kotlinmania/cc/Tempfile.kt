// port-lint: source tempfile.rs
package io.github.kotlinmania.cc

import kotlin.random.Random
import kotlin.random.nextULong

internal class FileAlreadyExistsException(
    message: String,
) : Exception(message)

internal interface FileHandle : AutoCloseable {
    fun write(bytes: ByteArray)

    fun flush()

    fun sync()

    override fun close()
}

internal fun rand(): ULong = Random.nextULong()

internal fun tmpname(suffix: String): String = "${rand()}$suffix"

internal expect fun createNamed(path: String): Result<FileHandle>

internal expect fun removeFile(path: String): Result<Unit>

internal class NamedTempfile internal constructor(
    private val path: String,
    private var file: FileHandle?,
) : AutoCloseable {
    fun path(): String = path

    fun takeFile(): FileHandle? {
        val f = file
        file = null
        return f
    }

    fun drop() {
        try {
            file?.close()
        } finally {
            file = null
            removeFile(path)
        }
    }

    override fun close() {
        drop()
    }

    companion object {
        fun new(base: String, suffix: String): Result<NamedTempfile> {
            val normalizedBase = if (base.isEmpty()) "." else base.trimEnd('/', '\\')
            var attempts = 0
            while (attempts < 10) {
                attempts++
                val candidateName = tmpname(suffix)
                val path = "$normalizedBase/$candidateName"
                val res = createNamed(path)
                if (res.isSuccess) {
                    return Result.success(NamedTempfile(path, res.getOrThrow()))
                }
                val ex = res.exceptionOrNull()
                if (ex is FileAlreadyExistsException) {
                    continue
                }
                return Result.failure(ex ?: IllegalStateException("Failed to create temporary file"))
            }
            return Result.failure(
                FileAlreadyExistsException("too many temporary files exist in base `$base` with suffix `$suffix`"),
            )
        }
    }
}
