// port-lint: source parallel/command_runner.rs
package io.github.kotlinmania.cc.parallel

import io.github.kotlinmania.cc.CargoOutput
import io.github.kotlinmania.cc.Child
import io.github.kotlinmania.cc.Command
import io.github.kotlinmania.cc.Error
import io.github.kotlinmania.cc.ErrorKind
import io.github.kotlinmania.cc.StderrForwarder
import io.github.kotlinmania.cc.spawn
import io.github.kotlinmania.cc.waitOnChild

internal class KillOnDrop(
    val child: Child,
    val stderrForwarder: StderrForwarder,
) : AutoCloseable {
    fun drop() {
        // Child process cleanup on drop
    }

    override fun close() {
        drop()
    }
}

internal fun <T> cellUpdate(
    current: T,
    f: (T) -> T,
): T = f(current)

internal fun tryWaitOnChild(
    cmd: Command,
    child: Child,
    stderrForwarder: StderrForwarder,
): Result<Unit?> {
    stderrForwarder.forwardAvailable()
    val status =
        child.wait().getOrElse { e ->
            stderrForwarder.forwardAll()
            return Result.failure(
                Error.new(
                    ErrorKind.ToolExecError,
                    "failed to wait on spawned child process `$cmd`: $e",
                ),
            )
        }
    stderrForwarder.forwardAll()
    return if (status.success) {
        Result.success(Unit)
    } else {
        Result.failure(
            Error.new(
                ErrorKind.ToolExecError,
                "command did not execute successfully (status code $status): $cmd",
            ),
        )
    }
}

internal fun runCommandsInParallel(
    cargoOutput: CargoOutput,
    cmds: Iterator<Result<Command>>,
): Result<Unit> {
    val activeTokens = ActiveJobTokenServer.new()
    for (cmdResult in cmds) {
        val cmd = cmdResult.getOrElse { return Result.failure(it) }
        val token = activeTokens.acquire()
        try {
            val child = spawn(cmd, cargoOutput).getOrElse { return Result.failure(it) }
            waitOnChild(cmd, child, cargoOutput).getOrElse { return Result.failure(it) }
        } finally {
            token.drop()
        }
    }
    return Result.success(Unit)
}
