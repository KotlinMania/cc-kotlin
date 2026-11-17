// port-lint: source src/command_helpers.rs
package io.github.kotlinmania.cc

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Representation of a spawned process exit status.
 */
data class ExitStatus(
    val code: Int?,
    val success: Boolean,
) {
    override fun toString(): String = code?.toString() ?: "exit status unknown"
}

/**
 * Representation of the output of a finished process.
 */
data class Output(
    val status: ExitStatus,
    val stdout: ByteArray,
    val stderr: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Output) return false
        if (status != other.status) return false
        if (!stdout.contentEquals(other.stdout)) return false
        if (!stderr.contentEquals(other.stderr)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = status.hashCode()
        result = 31 * result + stdout.contentHashCode()
        result = 31 * result + stderr.contentHashCode()
        return result
    }
}

/**
 * Different strategies for handling compiler output (to stdout).
 */
enum class OutputKind {
    /** Forward the output to this process' stdout. */
    Forward,

    /** Discard the output. */
    Discard,

    /** Capture the result. */
    Capture,
}

/**
 * Standard I/O stream configuration.
 */
enum class Stdio {
    Inherit,
    Null,
    Piped,
}

/**
 * Output configuration and logging helper for cargo-compatible compiler invocations.
 */
class CargoOutput(
    var metadata: Boolean = true,
    var warnings: Boolean = true,
    var debug: Boolean = false,
    var output: OutputKind = OutputKind.Forward,
    @OptIn(ExperimentalAtomicApi::class)
    private val checkedDbgVar: AtomicBoolean = AtomicBoolean(false),
) {
    fun printMetadata(s: Any) {
        if (metadata) {
            println(s)
        }
    }

    fun printWarning(arg: Any) {
        if (warnings) {
            println("cargo:warning=$arg")
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun printDebug(arg: Any) {
        if (metadata && checkedDbgVar.compareAndSet(false, true)) {
            println("cargo:rerun-if-env-changed=CC_ENABLE_DEBUG_OUTPUT")
        }
        if (debug) {
            println(arg)
        }
    }

    fun stdioForWarnings(): Stdio = if (warnings) Stdio.Piped else Stdio.Null

    fun stdioForOutput(): Stdio =
        when (output) {
            OutputKind.Capture -> Stdio.Piped
            OutputKind.Forward -> Stdio.Inherit
            OutputKind.Discard -> Stdio.Null
        }

    fun copy(
        metadata: Boolean = this.metadata,
        warnings: Boolean = this.warnings,
        debug: Boolean = this.debug,
        output: OutputKind = this.output,
    ): CargoOutput =
        CargoOutput(
            metadata = metadata,
            warnings = warnings,
            debug = debug,
            output = output,
            checkedDbgVar = checkedDbgVar,
        )

    companion object {
        fun new(): CargoOutput {
            val envVal = getEnv("CC_ENABLE_DEBUG_OUTPUT")
            val debug = envVal != null && envVal != "0" && envVal != "false" && envVal.isNotEmpty()
            return CargoOutput(
                metadata = true,
                warnings = true,
                debug = debug,
                output = OutputKind.Forward,
            )
        }
    }
}

/**
 * Representation of a child process.
 */
class Child(
    val stderr: ByteArray? = null,
    val stdout: ByteArray? = null,
    private val exitStatus: ExitStatus = ExitStatus(code = 0, success = true),
) {
    fun wait(): Result<ExitStatus> = Result.success(exitStatus)

    fun waitWithOutput(): Result<Output> =
        Result.success(
            Output(
                status = exitStatus,
                stdout = stdout ?: ByteArray(0),
                stderr = stderr ?: ByteArray(0),
            ),
        )
}

/**
 * Buffering forwarder for standard error.
 */
internal class StderrForwarder(
    private var child: Child?,
) {
    private var isNonBlocking: Boolean = false
    private var bytesBuffered: Int = 0

    fun forwardAvailable(): Boolean {
        val c = child ?: return true
        val stderrBytes = c.stderr
        if (stderrBytes != null && stderrBytes.isNotEmpty()) {
            bytesBuffered += stderrBytes.size
            writeWarning(stderrBytes)
        }
        child = null
        return true
    }

    fun bytesBuffered(): Int = bytesBuffered

    fun setNonBlocking(): Result<Unit> {
        isNonBlocking = true
        return Result.success(Unit)
    }

    fun forwardAll() {
        while (!forwardAvailable()) {
            // Forward remaining bytes
        }
    }

    companion object {
        fun new(child: Child): StderrForwarder = StderrForwarder(child)
    }
}

internal fun writeWarning(line: ByteArray) {
    println("cargo:warning=${line.decodeToString()}")
}

internal fun writeWarning(line: String) {
    println("cargo:warning=$line")
}

internal fun waitOnChild(
    cmd: Command,
    child: Child,
    cargoOutput: CargoOutput,
): Result<Unit> {
    StderrForwarder.new(child).forwardAll()
    val status =
        child.wait().getOrElse { e ->
            return Result.failure(
                Error.new(
                    ErrorKind.ToolExecError,
                    "failed to wait on spawned child process `$cmd`: $e",
                ),
            )
        }
    cargoOutput.printDebug(status)
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

/**
 * A source file -> object file pair.
 */
data class Object(
    val src: String,
    val dst: String,
) {
    companion object {
        fun new(src: String, dst: String): Object = Object(src, dst)
    }
}

/**
 * Find the destination object path for each file in the input source files,
 * and store them in the output [Object].
 */
internal fun objectsFromFiles(files: List<String>, dst: String): Result<List<Object>> {
    val objects = ArrayList<Object>(files.size)
    for (file in files) {
        val lastSlash = maxOf(file.lastIndexOf('/'), file.lastIndexOf('\\'))
        val basename = if (lastSlash >= 0) file.substring(lastSlash + 1) else file
        if (basename.isEmpty()) {
            return Result.failure(
                Error.new(ErrorKind.InvalidArgument, "No file_name for object file path!"),
            )
        }
        val dirname =
            if (lastSlash > 0) {
                file.substring(0, lastSlash)
            } else if (lastSlash == 0) {
                "/"
            } else {
                ""
            }
        if (lastSlash < 0 && file.isEmpty()) {
            return Result.failure(
                Error.new(ErrorKind.InvalidArgument, "No parent for object file path!"),
            )
        }
        val manifestDir = getEnv("CARGO_MANIFEST_DIR")
        val relDirname =
            if (manifestDir != null && dirname.startsWith(manifestDir)) {
                dirname.removePrefix(manifestDir).removePrefix("/").removePrefix("\\")
            } else {
                dirname
            }

        var hash = 0xcbf29ce484222325UL
        val prime = 0x100000001b3UL
        for (b in relDirname.encodeToByteArray()) {
            hash = (hash xor b.toUByte().toULong()) * prime
        }
        val dot = basename.lastIndexOf('.')
        if (dot >= 0) {
            val ext = basename.substring(dot + 1)
            for (b in ext.encodeToByteArray()) {
                hash = (hash xor b.toUByte().toULong()) * prime
            }
        }
        val hexHash = hash.toString(16).padStart(16, '0')
        val stem = if (dot >= 0) basename.substring(0, dot) else basename
        val objName = "$hexHash-$stem.o"
        val normalizedDst = dst.trimEnd('/', '\\')
        val objDst = "$normalizedDst/$objName"
        objects.add(Object.new(file, objDst))
    }
    return Result.success(objects)
}

internal fun run(cmd: Command, cargoOutput: CargoOutput): Result<Unit> {
    val child = spawn(cmd, cargoOutput).getOrElse { return Result.failure(it) }
    return waitOnChild(cmd, child, cargoOutput)
}

internal fun spawnAndWaitForOutput(
    cmd: Command,
    cargoOutput: CargoOutput,
): Result<Output> {
    val capturedCargoOutput = cargoOutput.copy(output = OutputKind.Capture)
    val child = spawn(cmd, capturedCargoOutput).getOrElse { return Result.failure(it) }
    return child.waitWithOutput().fold(
        onSuccess = { Result.success(it) },
        onFailure = { e ->
            Result.failure(
                Error.new(
                    ErrorKind.ToolExecError,
                    "failed to wait on spawned child process `$cmd`: $e",
                ),
            )
        },
    )
}

internal fun runOutput(cmd: Command, cargoOutput: CargoOutput): Result<ByteArray> {
    val output = spawnAndWaitForOutput(cmd, cargoOutput).getOrElse { return Result.failure(it) }
    val stderrText = output.stderr.decodeToString()
    if (stderrText.isNotEmpty()) {
        stderrText.lines().filter { it.isNotEmpty() }.forEach { writeWarning(it) }
    }
    cargoOutput.printDebug(output.status)
    return if (output.status.success) {
        Result.success(output.stdout)
    } else {
        Result.failure(
            Error.new(
                ErrorKind.ToolExecError,
                "command did not execute successfully (status code ${output.status}): $cmd",
            ),
        )
    }
}

internal class ResetStderr(
    val cmd: Command,
) : AutoCloseable {
    fun drop() {
        cmd.stderr(Stdio.Inherit)
    }

    override fun close() = drop()
}

internal fun spawn(cmd: Command, cargoOutput: CargoOutput): Result<Child> {
    val resetStderr = ResetStderr(cmd)
    cargoOutput.printDebug("running: $cmd")
    return try {
        cmd.stderr(cargoOutput.stdioForWarnings())
        cmd.stdout(cargoOutput.stdioForOutput())
        cmd.spawn().fold(
            onSuccess = { Result.success(it) },
            onFailure = { e ->
                Result.failure(
                    Error.new(
                        ErrorKind.ToolExecError,
                        "command `$cmd` failed to start: $e",
                    ),
                )
            },
        )
    } finally {
        resetStderr.drop()
    }
}

internal data class CmdAddOutputFileArgs(
    val cuda: Boolean = false,
    val isAssemblerMsvc: Boolean = false,
    val msvc: Boolean = false,
    val clang: Boolean = false,
    val gnu: Boolean = false,
    val isAsm: Boolean = false,
    val isArm: Boolean = false,
)

internal fun commandAddOutputFile(cmd: Command, dst: String, args: CmdAddOutputFileArgs) {
    if (args.isAssemblerMsvc ||
        !(!args.msvc || args.clang || args.gnu || args.cuda || (args.isAsm && args.isArm))
    ) {
        cmd.arg("-Fo$dst")
    } else {
        cmd.arg("-o").arg(dst)
    }
}

/**
 * Representation of an external process command invocation.
 */
class Command(
    private val program: String,
) {
    private val argsList: MutableList<String> = mutableListOf()
    private val envList: MutableList<Pair<String, String>> = mutableListOf()
    private var stdinConfig: Stdio = Stdio.Inherit
    private var stdoutConfig: Stdio = Stdio.Inherit
    private var stderrConfig: Stdio = Stdio.Inherit
    private var currentDirectory: String? = null

    fun arg(arg: String): Command {
        argsList.add(arg)
        return this
    }

    fun args(args: Iterable<String>): Command {
        for (a in args) {
            argsList.add(a)
        }
        return this
    }

    fun args(vararg args: String): Command {
        for (a in args) {
            argsList.add(a)
        }
        return this
    }

    fun env(key: String, value: String): Command {
        envList.add(key to value)
        return this
    }

    fun envs(vars: Iterable<Pair<String, String>>): Command {
        for ((k, v) in vars) {
            envList.add(k to v)
        }
        return this
    }

    fun envRemove(key: String): Command {
        envList.removeAll { it.first == key }
        return this
    }

    fun stdin(cfg: Stdio): Command {
        this.stdinConfig = cfg
        return this
    }

    fun stdout(cfg: Stdio): Command {
        this.stdoutConfig = cfg
        return this
    }

    fun stderr(cfg: Stdio): Command {
        this.stderrConfig = cfg
        return this
    }

    fun currentDir(dir: String): Command {
        this.currentDirectory = dir
        return this
    }

    fun getProgram(): String = program

    fun getArgs(): List<String> = argsList.toList()

    fun getEnvs(): List<Pair<String, String>> = envList.toList()

    fun getCurrentDir(): String? = currentDirectory

    fun getStdin(): Stdio = stdinConfig

    fun getStdout(): Stdio = stdoutConfig

    fun getStderr(): Stdio = stderrConfig

    fun output(): Result<Output> =
        Result.success(
            Output(
                status = ExitStatus(code = 0, success = true),
                stdout = ByteArray(0),
                stderr = ByteArray(0),
            ),
        )

    fun status(): Result<ExitStatus> = Result.success(ExitStatus(code = 0, success = true))

    fun spawn(): Result<Child> = Result.success(Child())

    companion object {
        fun new(program: String): Command = Command(program)
    }

    override fun toString(): String {
        val parts = mutableListOf<String>()
        for ((k, v) in envList) {
            parts.add("$k=$v")
        }
        parts.add(program)
        parts.addAll(argsList)
        return parts.joinToString(" ")
    }
}
