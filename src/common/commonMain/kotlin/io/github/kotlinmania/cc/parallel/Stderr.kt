// port-lint: source parallel/stderr.rs
package io.github.kotlinmania.cc.parallel

import io.github.kotlinmania.cc.Child

internal fun getFlags(fd: Int): Result<Int> = Result.success(fd)

internal fun setFlags(
    fd: Int,
    flags: Int,
): Result<Unit> = Result.success(Unit)

internal fun setNonBlocking(pipe: Int): Result<Unit> = Result.success(Unit)

internal fun bytesAvailable(stderr: Child): Result<Int> {
    val bytes = stderr.stderr?.size ?: 0
    return Result.success(bytes)
}
