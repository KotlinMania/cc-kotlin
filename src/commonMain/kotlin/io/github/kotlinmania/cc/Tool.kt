// port-lint: source src/tool.rs
package io.github.kotlinmania.cc

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal expect fun isXctoolchainClangPlatform(path: String): Boolean

internal typealias CompilerFamilyLookupCache = MutableMap<List<String>, ToolFamily>

private const val DETECT_COMPILER_FAMILY_C: String = """#ifdef __clang__
#pragma message "clang"
#endif

#ifdef __GNUC__
#pragma message "gcc"
#endif

#ifdef __EMSCRIPTEN__
#pragma message "emscripten"
#endif

#ifdef __VXWORKS__
#pragma message "VxWorks"
#endif
"""

/**
 * Thread-safe cache for compiler family lookups.
 */
class CompilerFamilyCache {
    @OptIn(ExperimentalAtomicApi::class)
    private val cache = AtomicReference<Map<List<String>, ToolFamily>>(emptyMap())

    @OptIn(ExperimentalAtomicApi::class)
    fun get(key: List<String>): ToolFamily? = cache.load()[key]

    @OptIn(ExperimentalAtomicApi::class)
    fun insert(
        key: List<String>,
        family: ToolFamily,
    ) {
        while (true) {
            val current = cache.load()
            val updated = current + (key to family)
            if (cache.compareAndSet(current, updated)) {
                break
            }
        }
    }
}

/**
 * Interface representing external MSVC tool findings.
 */
interface FindMsvcTool {
    fun path(): String

    fun isClangCl(): Boolean

    fun env(): List<Pair<String, String>>
}

/**
 * Represents the family of tools this tool belongs to.
 *
 * Each family of tools differs in how and what arguments they accept.
 * Detection of a family is done on best-effort basis and may not accurately reflect the tool.
 */
sealed interface ToolFamily {
    /** Tool is GNU Compiler Collection-like. */
    data object Gnu : ToolFamily

    /**
     * Tool is Clang-like. It differs from GCC in that it accepts a superset of flags
     * and its cross-compilation approach is different.
     */
    data class Clang(
        val zigCc: Boolean = false,
    ) : ToolFamily

    /** Tool is the MSVC cl.exe. */
    data class Msvc(
        val clangCl: Boolean = false,
    ) : ToolFamily

    /** What the flag to request debug info for this family of tools looks like. */
    fun addDebugFlags(
        cmd: Tool,
        debugOpt: String,
        dwarfVersion: UInt? = null,
    ) {
        when (this) {
            is Msvc -> {
                cmd.pushCcArg("-Z7")
            }
            Gnu, is Clang -> {
                when (debugOpt) {
                    "", "0", "false", "none" -> {}
                    "line-directives-only" -> {
                        if (cmd.isLikeClang()) {
                            cmd.pushCcArg("-gline-directives-only")
                        } else {
                            cmd.pushCcArg("-g1")
                        }
                    }
                    "1", "limited", "line-tables-only" -> {
                        cmd.pushCcArg("-g1")
                    }
                    "2", "true", "full" -> {
                        cmd.pushCcArg("-g")
                    }
                    else -> {
                        cmd.pushCcArg("-g")
                    }
                }
                if (dwarfVersion != null) {
                    cmd.pushCcArg("-gdwarf-$dwarfVersion")
                }
            }
        }
    }

    /** What the flag to force frame pointers looks like. */
    fun addForceFramePointer(cmd: Tool) {
        when (this) {
            Gnu, is Clang -> {
                cmd.pushCcArg("-fno-omit-frame-pointer")
            }
            is Msvc -> {}
        }
    }

    /** Flag to enable all warnings. */
    fun warningsFlags(): String =
        when (this) {
            is Msvc -> "-W4"
            Gnu, is Clang -> "-Wall"
        }

    /** Flag to suppress all warnings. */
    fun warningsSuppressionFlags(): String =
        when (this) {
            is Msvc -> "-W0"
            Gnu, is Clang -> "-w"
        }

    /** Flag to enable extra warnings. */
    fun extraWarningsFlags(): String? =
        when (this) {
            is Msvc -> null
            Gnu, is Clang -> "-Wextra"
        }

    /** Flag to turn warnings into errors. */
    fun warningsToErrorsFlag(): String =
        when (this) {
            is Msvc -> "-WX"
            Gnu, is Clang -> "-Werror"
        }

    /** Whether stderr should be verbose. */
    fun verboseStderr(): Boolean = this is Clang
}

/**
 * Configuration used to represent an invocation of a C compiler.
 */
class Tool internal constructor(
    internal val path: String,
    internal var ccWrapperPath: String? = null,
    internal val ccWrapperArgs: MutableList<String> = mutableListOf(),
    internal val args: MutableList<String> = mutableListOf(),
    internal val env: MutableList<Pair<String, String>> = mutableListOf(),
    internal val family: ToolFamily,
    internal val cuda: Boolean = false,
    internal val removedArgs: MutableList<String> = mutableListOf(),
    internal var hasInternalTargetArg: Boolean = false,
) {
    /** Add an argument to be stripped from the final command arguments. */
    fun removeArg(flag: String) {
        removedArgs.add(flag)
    }

    /** Push an exotic or host flag to the end of the compiler's arguments list. */
    fun pushCcArg(flag: String) {
        if (cuda) {
            args.add("-Xcompiler")
        }
        args.add(flag)
    }

    /** Checks if an argument or flag has already been specified or conflicts. */
    fun isDuplicateOptArg(flag: String): Boolean {
        if (flag.isEmpty()) return false
        val chars = flag.iterator()
        val first = chars.next()

        if (isLikeMsvc()) {
            if (first != '/') return false
        } else if ((isLikeGnu() || isLikeClang()) && first != '-') {
            return false
        }

        if (chars.hasNext() && chars.next() == 'O') {
            return args().any { a -> a.length > 1 && a[1] == 'O' }
        }

        return false
    }

    /** Don't push optimization arg if it conflicts with existing args. */
    fun pushOptUnlessDuplicate(flag: String) {
        if (!isDuplicateOptArg(flag)) {
            pushCcArg(flag)
        }
    }

    /** Converts this compiler into a [Command] that's ready to be run. */
    fun toCommand(): Command {
        val cmd =
            ccWrapperPath?.let { wrapper ->
                val c = Command.new(wrapper)
                c.arg(path)
                c
            } ?: Command.new(path)

        cmd.args(ccWrapperArgs)
        cmd.args(args.filter { it !in removedArgs })
        for ((k, v) in env) {
            cmd.env(k, v)
        }
        return cmd
    }

    /** Returns the path for this compiler. */
    fun path(): String = path

    /** Returns the default set of arguments to the compiler. */
    fun args(): List<String> = args.toList()

    /** Returns the set of environment variables needed for this compiler to operate. */
    fun env(): List<Pair<String, String>> = env.toList()

    /** Returns the compiler command in the format of the CC environment variable. */
    fun ccEnv(): String {
        val wrapper = ccWrapperPath ?: return ""
        val sb = StringBuilder(wrapper)
        sb.append(' ')
        sb.append(path)
        for (arg in ccWrapperArgs) {
            sb.append(' ')
            sb.append(arg)
        }
        return sb.toString()
    }

    /** Returns the compiler flags in the format of the CFLAGS environment variable. */
    fun cflagsEnv(): String {
        val flags = StringBuilder()
        for ((i, arg) in args.withIndex()) {
            if (i > 0) flags.append(' ')
            flags.append(arg)
        }
        return flags.toString()
    }

    /** Whether the tool is GNU Compiler Collection-like. */
    fun isLikeGnu(): Boolean = family == ToolFamily.Gnu

    /** Whether the tool is Clang-like. */
    fun isLikeClang(): Boolean = family is ToolFamily.Clang

    /** Whether the tool is AppleClang under .xctoolchain. */
    fun isXctoolchainClang(): Boolean = isXctoolchainClangPlatform(path)

    /** Whether the tool is MSVC-like. */
    fun isLikeMsvc(): Boolean = family is ToolFamily.Msvc

    /** Whether the tool is clang-cl-based MSVC-like. */
    fun isLikeClangCl(): Boolean = family is ToolFamily.Msvc && family.clangCl

    /** Supports using `--` delimiter to separate arguments and path to source files. */
    fun supportsPathDelimiter(): Boolean = isLikeClangCl() && !cuda

    companion object {
        fun fromFindMsvcTools(tool: FindMsvcTool): Tool {
            val ccTool =
                withFamily(
                    tool.path(),
                    ToolFamily.Msvc(clangCl = tool.isClangCl()),
                )
            ccTool.env.addAll(tool.env())
            return ccTool
        }

        fun new(
            path: String,
            cachedCompilerFamily: CompilerFamilyCache,
            cargoOutput: CargoOutput,
            outDir: String? = null,
        ): Tool =
            withFeatures(
                path = path,
                args = emptyList(),
                cuda = false,
                cachedCompilerFamily = cachedCompilerFamily,
                cargoOutput = cargoOutput,
                outDir = outDir,
            )

        fun withArgs(
            path: String,
            args: List<String>,
            cachedCompilerFamily: CompilerFamilyCache,
            cargoOutput: CargoOutput,
            outDir: String? = null,
        ): Tool =
            withFeatures(
                path = path,
                args = args,
                cuda = false,
                cachedCompilerFamily = cachedCompilerFamily,
                cargoOutput = cargoOutput,
                outDir = outDir,
            )

        fun withFamily(
            path: String,
            family: ToolFamily,
        ): Tool =
            Tool(
                path = path,
                ccWrapperPath = null,
                ccWrapperArgs = mutableListOf(),
                args = mutableListOf(),
                env = mutableListOf(),
                family = family,
                cuda = false,
                removedArgs = mutableListOf(),
                hasInternalTargetArg = false,
            )

        fun withFeatures(
            path: String,
            args: List<String>,
            cuda: Boolean,
            cachedCompilerFamily: CompilerFamilyCache,
            cargoOutput: CargoOutput,
            outDir: String? = null,
        ): Tool {
            fun isZigCc(
                cmdPath: String,
                output: CargoOutput,
            ): Boolean {
                val versionOutput =
                    runOutput(
                        Command.new(cmdPath).arg("--version"),
                        output,
                    ).getOrNull()?.decodeToString()
                if (versionOutput != null && versionOutput.contains("ziglang")) {
                    return true
                }
                val fname = cmdPath.substringAfterLast('/').substringAfterLast('\\')
                return fname.contains("zig")
            }

            fun guessFamilyFromStdout(
                stdout: String,
                cmdPath: String,
                cmdArgs: List<String>,
                output: CargoOutput,
            ): Result<ToolFamily> {
                output.printDebug(stdout)

                val acceptsClStyleFlags =
                    run(
                        Command
                            .new(cmdPath)
                            .args(cmdArgs)
                            .arg("-?")
                            .stdin(Stdio.Null),
                        output.copy(
                            warnings = output.debug,
                            output = OutputKind.Discard,
                        ),
                    ).isSuccess

                val clang = stdout.contains("\"clang\"")
                val gcc = stdout.contains("\"gcc\"")
                val emscripten = stdout.contains("\"emscripten\"")
                val vxworks = stdout.contains("\"VxWorks\"")

                return when {
                    acceptsClStyleFlags && !emscripten && !vxworks ->
                        Result.success(ToolFamily.Msvc(clangCl = clang))
                    (clang && !vxworks) || (emscripten && !vxworks) ->
                        Result.success(ToolFamily.Clang(zigCc = isZigCc(cmdPath, output)))
                    (!acceptsClStyleFlags && gcc && !vxworks) || vxworks ->
                        Result.success(ToolFamily.Gnu)
                    else -> {
                        output.printWarning(
                            "Compiler family detection failed since it does not define `__clang__`, `__GNUC__`, " +
                                "`__EMSCRIPTEN__` or `__VXWORKS__`, also does not accept cl style flag `-?`, " +
                                "fallback to treating it as GNU",
                        )
                        Result.failure(
                            Error.new(
                                ErrorKind.ToolFamilyMacroNotFound,
                                "Expects macro `__clang__`, `__GNUC__` or `__EMSCRIPTEN__`, `__VXWORKS__` " +
                                    "or accepts cl style flag `-?`, but found none",
                            ),
                        )
                    }
                }
            }

            fun detectFamilyInner(
                cmdPath: String,
                cmdArgs: List<String>,
                output: CargoOutput,
                targetOutDir: String?,
            ): Result<ToolFamily> {
                val dir = targetOutDir ?: getEnv("TMPDIR") ?: getEnv("TEMP") ?: getEnv("TMP") ?: "."
                val tmp =
                    NamedTempfile.new(dir, "detect_compiler_family.c").getOrElse { err ->
                        return Result.failure(
                            Error.new(
                                ErrorKind.IOError,
                                "failed to create detect_compiler_family.c temp file in '$dir': $err",
                            ),
                        )
                    }

                val tmpFile = tmp.takeFile()
                if (tmpFile != null) {
                    tmpFile.use { handle ->
                        handle.write(DETECT_COMPILER_FAMILY_C.encodeToByteArray())
                        handle.flush()
                        handle.sync()
                    }
                }

                val compilerDetectOutput = output.copy(warnings = output.debug)
                val cmd = Command.new(cmdPath)
                cmd.arg("-E").arg(tmp.path())

                val capturedCargoOutput = compilerDetectOutput.copy(warnings = true)
                val resOutput =
                    spawnAndWaitForOutput(cmd, capturedCargoOutput).getOrElse { return Result.failure(it) }

                val stdoutStr = resOutput.stdout.decodeToString()
                val stderrStr = resOutput.stderr.decodeToString()

                val stdout =
                    if (stdoutStr.contains("-Wslash-u-filename") || stderrStr.contains("-Wslash-u-filename")) {
                        runOutput(
                            Command
                                .new(cmdPath)
                                .arg("-E")
                                .arg("--")
                                .arg(tmp.path()),
                            compilerDetectOutput,
                        ).getOrElse { return Result.failure(it) }
                    } else {
                        if (!resOutput.status.success) {
                            return Result.failure(
                                Error.new(
                                    ErrorKind.ToolExecError,
                                    "command did not execute successfully (status code ${resOutput.status}): $cmd",
                                ),
                            )
                        }
                        resOutput.stdout
                    }

                return guessFamilyFromStdout(stdout.decodeToString(), cmdPath, cmdArgs, output)
            }

            fun detectFamily(
                cmdPath: String,
                cmdArgs: List<String>,
            ): Result<ToolFamily> {
                val cacheKey = listOf(cmdPath) + cmdArgs
                val cached = cachedCompilerFamily.get(cacheKey)
                if (cached != null) {
                    return Result.success(cached)
                }

                val detected = detectFamilyInner(cmdPath, cmdArgs, cargoOutput, outDir)
                if (detected.isSuccess) {
                    cachedCompilerFamily.insert(cacheKey, detected.getOrThrow())
                }
                return detected
            }

            val family =
                detectFamily(path, args).getOrElse { e ->
                    cargoOutput.printWarning("Compiler family detection failed due to error: $e")
                    val fname = path.substringAfterLast('/').substringAfterLast('\\')
                    when {
                        fname.contains("clang-cl") -> ToolFamily.Msvc(clangCl = true)
                        fname.endsWith("cl") || fname == "cl.exe" -> ToolFamily.Msvc(clangCl = false)
                        fname.contains("clang") -> {
                            val isClangCl =
                                args.any {
                                    it.startsWith("--driver-mode=") &&
                                        it.removePrefix("--driver-mode=") == "cl"
                                }
                            if (isClangCl) {
                                ToolFamily.Msvc(clangCl = true)
                            } else {
                                ToolFamily.Clang(zigCc = isZigCc(path, cargoOutput))
                            }
                        }
                        fname.contains("zig") -> ToolFamily.Clang(zigCc = true)
                        else -> ToolFamily.Gnu
                    }
                }

            return Tool(
                path = path,
                ccWrapperPath = null,
                ccWrapperArgs = mutableListOf(),
                args = mutableListOf(),
                env = mutableListOf(),
                family = family,
                cuda = cuda,
                removedArgs = mutableListOf(),
                hasInternalTargetArg = false,
            )
        }
    }
}
