package io.github.kotlinmania.cc

private fun getNodeFs(): dynamic =
    js(
        "(function(){ try { var r = typeof __non_webpack_require__ !== 'undefined' ? __non_webpack_require__ : (typeof require === 'function' ? require : null); return r ? r('fs') : null; } catch (e) { return null; } })()",
    )

private val inMemoryFiles = mutableMapOf<String, ByteArray>()

internal class JsFileHandle(
    private val path: String,
    private val fd: Int?,
    private val fs: dynamic,
) : FileHandle {
    private val buffer = mutableListOf<Byte>()

    override fun write(bytes: ByteArray) {
        if (fs != null && fd != null) {
            val nodeBuf: dynamic = js("Buffer.from(bytes)")
            fs.writeSync(fd, nodeBuf)
        } else {
            for (b in bytes) {
                buffer.add(b)
            }
            inMemoryFiles[path] = buffer.toByteArray()
        }
    }

    override fun flush() {}

    override fun sync() {
        if (fs != null && fd != null) {
            fs.fsyncSync(fd)
        }
    }

    override fun close() {
        if (fs != null && fd != null) {
            fs.closeSync(fd)
        }
    }
}

internal actual fun createNamed(path: String): Result<FileHandle> =
    runCatching {
        val fs = getNodeFs()
        if (fs != null) {
            val parent = path.substringBeforeLast('/', "")
            if (parent.isNotEmpty() && parent != ".") {
                try {
                    fs.mkdirSync(parent, js("({ recursive: true })"))
                } catch (_: Throwable) {
                }
            }
            try {
                val fd = fs.openSync(path, "wx+") as Int
                JsFileHandle(path, fd, fs)
            } catch (e: Throwable) {
                val msg = e.message ?: ""
                if (msg.contains("EEXIST")) {
                    throw FileAlreadyExistsException("File already exists: $path")
                }
                throw e
            }
        } else {
            if (inMemoryFiles.containsKey(path)) {
                throw FileAlreadyExistsException("File already exists: $path")
            }
            inMemoryFiles[path] = ByteArray(0)
            JsFileHandle(path, null, null)
        }
    }

internal actual fun removeFile(path: String): Result<Unit> =
    runCatching {
        val fs = getNodeFs()
        if (fs != null) {
            fs.unlinkSync(path)
        } else {
            if (!inMemoryFiles.containsKey(path)) {
                throw IllegalStateException("File not found: $path")
            }
            inMemoryFiles.remove(path)
        }
    }
