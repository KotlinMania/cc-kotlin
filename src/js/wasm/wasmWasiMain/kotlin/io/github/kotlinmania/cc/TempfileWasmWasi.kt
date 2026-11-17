package io.github.kotlinmania.cc

private val wasmWasiInMemoryFiles = mutableMapOf<String, ByteArray>()

internal class WasmWasiFileHandle(
    private val path: String,
) : FileHandle {
    private val buffer = mutableListOf<Byte>()

    override fun write(bytes: ByteArray) {
        for (b in bytes) {
            buffer.add(b)
        }
        wasmWasiInMemoryFiles[path] = buffer.toByteArray()
    }

    override fun flush() {}

    override fun sync() {}

    override fun close() {}
}

internal actual fun createNamed(path: String): Result<FileHandle> =
    runCatching {
        if (wasmWasiInMemoryFiles.containsKey(path)) {
            throw FileAlreadyExistsException("File already exists: $path")
        }
        wasmWasiInMemoryFiles[path] = ByteArray(0)
        WasmWasiFileHandle(path)
    }

internal actual fun removeFile(path: String): Result<Unit> =
    runCatching {
        if (!wasmWasiInMemoryFiles.containsKey(path)) {
            throw IllegalStateException("File not found: $path")
        }
        wasmWasiInMemoryFiles.remove(path)
    }
