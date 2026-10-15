package io.github.kotlinmania.cc

private val wasmJsInMemoryFiles = mutableMapOf<String, ByteArray>()

internal class WasmJsFileHandle(
    private val path: String,
) : FileHandle {
    private val buffer = mutableListOf<Byte>()

    override fun write(bytes: ByteArray) {
        for (b in bytes) {
            buffer.add(b)
        }
        wasmJsInMemoryFiles[path] = buffer.toByteArray()
    }

    override fun flush() {}

    override fun sync() {}

    override fun close() {}
}

internal actual fun createNamed(path: String): Result<FileHandle> =
    runCatching {
        if (wasmJsInMemoryFiles.containsKey(path)) {
            throw FileAlreadyExistsException("File already exists: $path")
        }
        wasmJsInMemoryFiles[path] = ByteArray(0)
        WasmJsFileHandle(path)
    }

internal actual fun removeFile(path: String): Result<Unit> =
    runCatching {
        if (!wasmJsInMemoryFiles.containsKey(path)) {
            throw IllegalStateException("File not found: $path")
        }
        wasmJsInMemoryFiles.remove(path)
    }
