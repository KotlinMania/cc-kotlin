package io.github.kotlinmania.cc

import io.github.kotlinmania.cc.parallel.runCommandsInParallel
import io.github.kotlinmania.cc.target.TargetInfo
import io.github.kotlinmania.cc.target.TargetInfoParser
import io.github.kotlinmania.cc.target.appleSdkName
import io.github.kotlinmania.cc.target.appleVersionFlag
import io.github.kotlinmania.cc.target.fromRustcTarget
import io.github.kotlinmania.shlex.Shlex
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * A version of Visual Studio.
 */
enum class VsVers {
    @Deprecated("Visual Studio 12 is no longer supported. cc will never return this value.")
    Vs12,
    Vs14,
    Vs15,
    Vs16,
    Vs17,
}

/**
 * Windows-specific tool discovery.
 */
object Windows {
    /**
     * Find the most recent installed version of Visual Studio.
     */
    fun findVsVersion(): Result<VsVers> =
        Result.failure(
            Error.new(
                ErrorKind.ToolExecError,
                "find_vs_version requires MSVC development tools on Windows hosts",
            ),
        )
}

fun findVsVersion(): Result<VsVers> = Windows.findVsVersion()

/**
 * A compiler and flag pair for caching flag support checks.
 */
data class CompilerFlag(
    val compiler: String,
    val flag: String,
)

/**
 * Thread-safe map for caching across compilations.
 */
class ThreadSafeMap<K, V> {
    @OptIn(ExperimentalAtomicApi::class)
    private val cache = AtomicReference<Map<K, V>>(emptyMap())

    @OptIn(ExperimentalAtomicApi::class)
    operator fun get(key: K): V? = cache.load()[key]

    @OptIn(ExperimentalAtomicApi::class)
    operator fun set(key: K, value: V) {
        while (true) {
            val current = cache.load()
            val updated = current + (key to value)
            if (cache.compareAndSet(current, updated)) {
                break
            }
        }
    }
}

/**
 * Thread-safe caches used during compilation and flag checks.
 */
class BuildCache {
    val envCache = ThreadSafeMap<String, String?>()
    val appleSdkRootCache = ThreadSafeMap<String, String>()
    val appleVersionsCache = ThreadSafeMap<String, String>()
    val cachedCompilerFamily = CompilerFamilyCache()
    val knownFlagSupportStatusCache = ThreadSafeMap<CompilerFlag, Boolean>()
    val targetInfoParser = TargetInfoParser()
}

/**
 * Provider hook for external MSVC tool resolution.
 */
fun interface FindMsvcToolsProvider {
    fun findToolWithEnv(arch: String, tool: String, envGetter: (String) -> String?): Tool?
}

var findMsvcToolProvider: FindMsvcToolsProvider? = null

/**
 * A builder for compilation of a native library.
 */
class Build {
    internal val includeDirectories: MutableList<String> = mutableListOf()
    internal val definitions: MutableList<Pair<String, String?>> = mutableListOf()
    internal val objects: MutableList<String> = mutableListOf()
    internal val flags: MutableList<String> = mutableListOf()
    internal val flagsSupported: MutableList<String> = mutableListOf()
    internal val arFlags: MutableList<String> = mutableListOf()
    internal val asmFlags: MutableList<String> = mutableListOf()
    var noDefaultFlags: Boolean = false
        internal set
    internal val files: MutableList<String> = mutableListOf()
    var cpp: Boolean = false
        internal set
    var cppLinkStdlib: String? = null
        internal set
    var cppLinkStdlibStatic: Boolean = false
        internal set
    var cppSetStdlib: String? = null
        internal set
    var cuda: Boolean = false
        internal set
    var cudart: String? = null
        internal set
    var ccbin: Boolean = true
        internal set
    var std: String? = null
        internal set
    var target: String? = null
        internal set
    var host: String? = null
        internal set
    var outDir: String? = null
        internal set
    var optLevel: String? = null
        internal set
    var debug: String? = null
        internal set
    var forceFramePointer: Boolean? = null
        internal set
    internal val env: MutableList<Pair<String, String>> = mutableListOf()
    var compiler: String? = null
        internal set
    var archiver: String? = null
        internal set
    var ranlib: String? = null
        internal set
    val cargoOutput: CargoOutput = CargoOutput()
    internal val linkLibModifiers: MutableList<String> = mutableListOf()
    var pic: Boolean? = null
        internal set
    var usePlt: Boolean? = null
        internal set
    var staticCrt: Boolean? = null
        internal set
    var sharedFlag: Boolean? = null
        internal set
    var staticFlag: Boolean? = null
        internal set
    var warningsIntoErrors: Boolean = false
        internal set
    var warnings: Boolean? = null
        internal set
    var extraWarnings: Boolean? = null
        internal set
    var emitRerunIfEnvChanged: Boolean = true
        internal set
    var shellEscapedFlags: Boolean? = null
        internal set
    val buildCache: BuildCache = BuildCache()
    var inheritRustflags: Boolean = true
        internal set
    var preferClangClOverMsvc: Boolean = false
        internal set

    constructor()

    companion object {
        fun new(): Build = Build()

        fun default(): Build = new()
    }

    fun include(dir: String): Build {
        includeDirectories.add(dir)
        return this
    }

    fun includes(dirs: Iterable<String>): Build {
        for (dir in dirs) {
            include(dir)
        }
        return this
    }

    fun define(varName: String, valName: String? = null): Build {
        definitions.add(varName to valName)
        return this
    }

    fun objectFile(obj: String): Build {
        objects.add(obj)
        return this
    }

    fun objects(objs: Iterable<String>): Build {
        for (obj in objs) {
            objectFile(obj)
        }
        return this
    }

    fun flag(flag: String): Build {
        flags.add(flag)
        return this
    }

    fun flags(flags: Iterable<String>): Build {
        for (f in flags) {
            flag(f)
        }
        return this
    }

    fun removeFlag(flag: String): Build {
        flags.removeAll { it == flag }
        return this
    }

    fun arFlag(flag: String): Build {
        arFlags.add(flag)
        return this
    }

    fun asmFlag(flag: String): Build {
        asmFlags.add(flag)
        return this
    }

    fun flagIfSupported(flag: String): Build {
        flagsSupported.add(flag)
        return this
    }

    fun tryFlagsFromEnvironment(environKey: String): Result<Build> =
        runCatching {
            val flags =
                envflags(environKey).getOrThrow() ?: throw Error.new(
                    ErrorKind.EnvVarNotFound,
                    "could not find environment variable $environKey",
                )
            this.flags.addAll(flags)
            this
        }

    fun sharedFlag(sharedFlag: Boolean): Build {
        this.sharedFlag = sharedFlag
        return this
    }

    fun staticFlag(staticFlag: Boolean): Build {
        this.staticFlag = staticFlag
        return this
    }

    fun noDefaultFlags(noDefaultFlags: Boolean): Build {
        this.noDefaultFlags = noDefaultFlags
        return this
    }

    fun file(p: String): Build {
        files.add(p)
        return this
    }

    fun files(p: Iterable<String>): Build {
        for (file in p) {
            file(file)
        }
        return this
    }

    @kotlin.jvm.JvmName("retrieveFiles")
    fun getFiles(): List<String> = files.toList()

    fun cpp(cpp: Boolean): Build {
        this.cpp = cpp
        return this
    }

    fun cuda(cuda: Boolean): Build {
        this.cuda = cuda
        if (cuda) {
            this.cpp = true
            this.cudart = "static"
        }
        return this
    }

    fun cudart(cudart: String): Build {
        if (this.cuda) {
            this.cudart = cudart
        }
        return this
    }

    fun ccbin(ccbin: Boolean): Build {
        this.ccbin = ccbin
        return this
    }

    fun std(std: String): Build {
        this.std = std
        return this
    }

    fun warningsIntoErrors(warningsIntoErrors: Boolean): Build {
        this.warningsIntoErrors = warningsIntoErrors
        return this
    }

    fun warnings(warnings: Boolean): Build {
        this.warnings = warnings
        this.extraWarnings = warnings
        return this
    }

    fun extraWarnings(warnings: Boolean): Build {
        this.extraWarnings = warnings
        return this
    }

    fun cppLinkStdlib(cppLinkStdlib: String?): Build {
        this.cppLinkStdlib = cppLinkStdlib
        return this
    }

    fun cppLinkStdlibStatic(isStatic: Boolean): Build {
        this.cppLinkStdlibStatic = isStatic
        return this
    }

    fun cppSetStdlib(cppSetStdlib: String?): Build {
        this.cppSetStdlib = cppSetStdlib
        this.cppLinkStdlib = cppSetStdlib
        return this
    }

    fun target(target: String): Build {
        this.target = target
        return this
    }

    fun host(host: String): Build {
        this.host = host
        return this
    }

    fun optLevel(optLevel: UInt): Build {
        this.optLevel = optLevel.toString()
        return this
    }

    fun optLevelStr(optLevel: String): Build {
        this.optLevel = optLevel
        return this
    }

    fun debug(debug: Boolean): Build {
        this.debug = debug.toString()
        return this
    }

    fun debugStr(debug: String): Build {
        this.debug = debug
        return this
    }

    fun forceFramePointer(force: Boolean): Build {
        this.forceFramePointer = force
        return this
    }

    fun outDir(outDir: String): Build {
        this.outDir = outDir
        return this
    }

    fun compiler(compiler: String): Build {
        this.compiler = compiler
        return this
    }

    fun archiver(archiver: String): Build {
        this.archiver = archiver
        return this
    }

    fun ranlib(ranlib: String): Build {
        this.ranlib = ranlib
        return this
    }

    fun cargoMetadata(cargoMetadata: Boolean): Build {
        this.cargoOutput.metadata = cargoMetadata
        return this
    }

    fun cargoWarnings(cargoWarnings: Boolean): Build {
        this.cargoOutput.warnings = cargoWarnings
        return this
    }

    fun cargoDebug(cargoDebug: Boolean): Build {
        this.cargoOutput.debug = cargoDebug
        return this
    }

    fun cargoOutput(cargoOutput: Boolean): Build {
        this.cargoOutput.output = if (cargoOutput) OutputKind.Forward else OutputKind.Discard
        return this
    }

    fun linkLibModifier(linkLibModifier: String): Build {
        linkLibModifiers.add(linkLibModifier)
        return this
    }

    fun pic(pic: Boolean): Build {
        this.pic = pic
        return this
    }

    fun usePlt(usePlt: Boolean): Build {
        this.usePlt = usePlt
        return this
    }

    fun emitRerunIfEnvChanged(emitRerunIfEnvChanged: Boolean): Build {
        this.emitRerunIfEnvChanged = emitRerunIfEnvChanged
        return this
    }

    fun staticCrt(staticCrt: Boolean): Build {
        this.staticCrt = staticCrt
        return this
    }

    fun shellEscapedFlags(shellEscapedFlags: Boolean): Build {
        this.shellEscapedFlags = shellEscapedFlags
        return this
    }

    fun inheritRustflags(inheritRustflags: Boolean): Build {
        this.inheritRustflags = inheritRustflags
        return this
    }

    fun preferClangClOverMsvc(preferClangClOverMsvc: Boolean): Build {
        this.preferClangClOverMsvc = preferClangClOverMsvc
        return this
    }

    fun setEnv(a: String, b: String): Build {
        env.add(a to b)
        return this
    }

    fun getTarget(): Result<TargetInfo> {
        val t = target
        val envTarget = getenvUnwrapStr("TARGET").getOrNull()
        return if (t != null && t != envTarget) {
            TargetInfo.fromRustcTarget(t)
        } else {
            buildCache.targetInfoParser.parseFromCargoEnvironmentVariables()
        }
    }

    fun getRawTarget(): Result<String> {
        val t = target
        return if (t != null) {
            Result.success(t)
        } else {
            getenvUnwrapStr("TARGET")
        }
    }

    fun getIsCrossCompile(): Result<Boolean> {
        val tgt = getRawTarget().getOrElse { return Result.failure(it) }
        val hst = host ?: getenvUnwrapStr("HOST").getOrElse { return Result.failure(it) }
        return Result.success(hst != tgt)
    }

    fun getOptLevel(): Result<String> {
        val ol = optLevel
        return if (ol != null) {
            Result.success(ol)
        } else {
            getenvUnwrapStr("OPT_LEVEL")
        }
    }

    fun getDebug(): Boolean =
        when (getDebugStr().getOrNull()) {
            null, "", "0", "false", "none" -> false
            else -> true
        }

    fun getDebugStr(): Result<String> {
        val d = debug
        return if (d != null) {
            Result.success(d)
        } else {
            getenvUnwrapStr("DEBUG")
        }
    }

    fun getShellEscapedFlags(): Boolean =
        shellEscapedFlags ?: getenvBoolean("CC_SHELL_ESCAPED_FLAGS")

    fun getDwarfVersion(): UInt? {
        val tgt = getTarget().getOrNull() ?: return null
        return when {
            tgt.os in setOf("android", "dragonfly", "freebsd", "netbsd", "openbsd") ||
                tgt.vendor == "apple" ||
                (tgt.os == "windows" && tgt.env == "gnu") -> 2u
            tgt.os == "linux" -> 4u
            else -> null
        }
    }

    fun getForceFramePointer(): Boolean = forceFramePointer ?: getDebug()

    fun getOutDir(): Result<String> {
        val od = outDir
        return if (od != null) {
            Result.success(od)
        } else {
            getenv("OUT_DIR")?.let { Result.success(it) } ?: Result.failure(
                Error.new(ErrorKind.EnvVarNotFound, "Environment variable OUT_DIR not defined."),
            )
        }
    }

    fun getenv(v: String): String? {
        fun providedByCargo(envvar: String): Boolean =
            when {
                envvar.startsWith("CARGO") || envvar.startsWith("RUSTC") -> true
                envvar in
                    setOf(
                        "HOST",
                        "TARGET",
                        "RUSTDOC",
                        "OUT_DIR",
                        "OPT_LEVEL",
                        "DEBUG",
                        "PROFILE",
                        "NUM_JOBS",
                        "RUSTFLAGS",
                    )
                -> true
                else -> false
            }

        val cachedVal = buildCache.envCache[v]
        if (cachedVal != null) {
            return cachedVal
        }

        if (emitRerunIfEnvChanged && !providedByCargo(v) && v != "PATH") {
            cargoOutput.printMetadata("cargo:rerun-if-env-changed=$v")
        }

        val r = env.find { it.first == v }?.second ?: getEnv(v)
        cargoOutput.printMetadata("$v = ${OptionOsStrDisplay(r)}")
        buildCache.envCache[v] = r
        return r
    }

    fun getenvBoolean(v: String): Boolean {
        val s = getenv(v) ?: return false
        return s != "0" && s != "false" && s.isNotEmpty()
    }

    fun getenvUnwrap(v: String): Result<String> {
        val s = getenv(v)
        return if (s != null) {
            Result.success(s)
        } else {
            Result.failure(Error.new(ErrorKind.EnvVarNotFound, "Environment variable $v not defined."))
        }
    }

    fun getenvUnwrapStr(v: String): Result<String> = getenvUnwrap(v)

    fun targetEnvs(envVar: String): Result<List<String>> {
        val tgt = getRawTarget().getOrElse { return Result.failure(it) }
        val kind = if (getIsCrossCompile().getOrDefault(false)) "TARGET" else "HOST"
        val targetU = tgt.replace('-', '_').replace('.', '_')
        return Result.success(
            listOf(
                "${envVar}_$tgt",
                "${envVar}_$targetU",
                "${kind}_$envVar",
                envVar,
            ),
        )
    }

    fun getenvWithTargetPrefixes(envVar: String): Result<String> {
        val envs = targetEnvs(envVar).getOrElse { return Result.failure(it) }
        for (e in envs) {
            val v = getenv(e)
            if (v != null) {
                return Result.success(v)
            }
        }
        return Result.failure(
            Error.new(ErrorKind.EnvVarNotFound, "could not find environment variable $envVar"),
        )
    }

    fun envflags(envVar: String): Result<List<String>?> {
        val envs = targetEnvs(envVar).getOrElse { return Result.failure(it) }
        var anySet = false
        val res = mutableListOf<String>()
        for (e in envs.asReversed()) {
            val v = getenv(e)
            if (v != null) {
                anySet = true
                if (getShellEscapedFlags()) {
                    val splitWords = Shlex(v).asSequence().toList()
                    res.addAll(splitWords)
                } else {
                    res.addAll(v.split("\\s+".toRegex()).filter { it.isNotEmpty() })
                }
            }
        }
        return Result.success(if (anySet) res else null)
    }

    fun fixEnvForAppleOs(cmd: Command): Result<Unit> {
        val tgt = getTarget().getOrElse { return Result.failure(it) }
        if (!isWindows() && tgt.os == "macos") {
            cmd.envRemove("IPHONEOS_DEPLOYMENT_TARGET")
        }
        return Result.success(Unit)
    }

    fun appleSdkRootInner(sdk: String): Result<String> {
        val sdkroot = getenv("SDKROOT")
        if (sdkroot != null) {
            val doesSdkrootContain = { strings: List<String> ->
                strings.any { sdkroot.contains(it) }
            }
            val valid =
                when (sdk) {
                    "appletvos" -> !doesSdkrootContain(listOf("TVSimulator.platform", "MacOSX.platform"))
                    "appletvsimulator" -> !doesSdkrootContain(listOf("TVOS.platform", "MacOSX.platform"))
                    "iphoneos" -> !doesSdkrootContain(listOf("iPhoneSimulator.platform", "MacOSX.platform"))
                    "iphonesimulator" -> !doesSdkrootContain(listOf("iPhoneOS.platform", "MacOSX.platform"))
                    "macosx10.15" -> !doesSdkrootContain(listOf("iPhoneOS.platform", "iPhoneSimulator.platform"))
                    "watchos" -> !doesSdkrootContain(listOf("WatchSimulator.platform", "MacOSX.platform"))
                    "watchsimulator" -> !doesSdkrootContain(listOf("WatchOS.platform", "MacOSX.platform"))
                    "xros" -> !doesSdkrootContain(listOf("XRSimulator.platform", "MacOSX.platform"))
                    "xrsimulator" -> !doesSdkrootContain(listOf("XROS.platform", "MacOSX.platform"))
                    else -> !sdkroot.startsWith("/") || sdkroot == "/" || fileExists(sdkroot)
                }
            if (valid && fileExists(sdkroot)) {
                return Result.success(sdkroot)
            }
        }

        val sdkPathBytes =
            runOutput(
                cmd("xcrun").arg("--show-sdk-path").arg("--sdk").arg(sdk),
                cargoOutput,
            ).getOrElse {
                return Result.failure(
                    Error.new(ErrorKind.IOError, "Unable to determine Apple SDK path."),
                )
            }
        val sdkPath = sdkPathBytes.decodeToString().trim()
        return Result.success(sdkPath)
    }

    fun appleSdkRoot(target: TargetInfo): Result<String> {
        val sdk = target.appleSdkName()
        val cached = buildCache.appleSdkRootCache[sdk]
        if (cached != null) {
            return Result.success(cached)
        }
        val sdkPath = appleSdkRootInner(sdk).getOrElse { return Result.failure(it) }
        buildCache.appleSdkRootCache[sdk] = sdkPath
        return Result.success(sdkPath)
    }

    fun appleDeploymentTarget(target: TargetInfo): String {
        val sdk = target.appleSdkName()
        val cached = buildCache.appleVersionsCache[sdk]
        if (cached != null) {
            return cached
        }

        val defaultDeploymentFromSdk = {
            val verBytes =
                runOutput(
                    cmd("xcrun").arg("--show-sdk-version").arg("--sdk").arg(sdk),
                    cargoOutput,
                ).getOrNull()
            verBytes?.decodeToString()?.trim()
        }

        val deploymentFromEnv = { name: String ->
            env.find { it.first == name }?.second ?: getenv(name)
        }

        val maybeCppVersionBaseline = { deploymentTargetVer: String ->
            if (!cpp) {
                deploymentTargetVer
            } else {
                val parts = deploymentTargetVer.split('.').mapNotNull { it.toUIntOrNull() }
                when (target.os) {
                    "macos" -> {
                        val major = parts.getOrNull(0) ?: 0u
                        val minor = parts.getOrNull(1) ?: 0u
                        if (major == 10u && minor < 9u) {
                            cargoOutput.printWarning(
                                "macOS deployment target ($deploymentTargetVer) too low, it will be increased",
                            )
                            null
                        } else {
                            deploymentTargetVer
                        }
                    }
                    "ios" -> {
                        val major = parts.getOrNull(0) ?: 0u
                        if (major < 7u) {
                            cargoOutput.printWarning(
                                "iOS deployment target ($deploymentTargetVer) too low, it will be increased",
                            )
                            null
                        } else {
                            deploymentTargetVer
                        }
                    }
                    else -> deploymentTargetVer
                }
            }
        }

        val version =
            when (target.os) {
                "macos" -> deploymentFromEnv("MACOSX_DEPLOYMENT_TARGET")?.let(maybeCppVersionBaseline)
                "ios" -> deploymentFromEnv("IPHONEOS_DEPLOYMENT_TARGET")?.let(maybeCppVersionBaseline)
                "tvos" -> deploymentFromEnv("TVOS_DEPLOYMENT_TARGET")
                "watchos" -> deploymentFromEnv("WATCHOS_DEPLOYMENT_TARGET")
                "visionos" -> deploymentFromEnv("XROS_DEPLOYMENT_TARGET")
                else -> null
            } ?: defaultDeploymentFromSdk() ?: "11.0"

        buildCache.appleVersionsCache[sdk] = version
        return version
    }

    fun wasmMuslSysroot(): Result<String> {
        val path = getenv("WASM_MUSL_SYSROOT")
        return if (path != null) {
            Result.success(path)
        } else {
            Result.failure(
                Error.new(
                    ErrorKind.EnvVarNotFound,
                    "Environment variable WASM_MUSL_SYSROOT not defined for wasm32.",
                ),
            )
        }
    }

    fun wasiSysroot(): Result<String> {
        val path = getenv("WASI_SYSROOT")
        return if (path != null) {
            Result.success(path)
        } else {
            Result.failure(
                Error.new(
                    ErrorKind.EnvVarNotFound,
                    "Environment variable WASI_SYSROOT not defined.",
                ),
            )
        }
    }

    fun cudaFileCount(): Int = files.count { it.endsWith(".cu") }

    fun which(tool: String, pathEntries: String? = null): String? {
        val findExeInPath = { paths: String ->
            paths.split(if (isWindows()) ';' else ':').firstNotNullOfOrNull { dir ->
                checkExe("$dir/$tool")
            }
        }
        return if (tool.contains('/') || tool.contains('\\')) {
            checkExe(tool)
        } else {
            pathEntries?.let(findExeInPath) ?: getenv("PATH")?.let(findExeInPath)
        }
    }

    fun searchPrograms(
        cc: String,
        prog: String,
        cargoOutput: CargoOutput,
    ): String? {
        val searchDirsBytes =
            runOutput(
                cmd(cc).arg("--print-search-dirs"),
                cargoOutput,
            ).getOrNull() ?: return null
        val searchDirs = searchDirsBytes.decodeToString()
        for (dirs in searchDirs.lines()) {
            if (dirs.startsWith("programs: =")) {
                val path = dirs.removePrefix("programs: =")
                return which(prog, path)
            }
        }
        return null
    }

    fun findMsvcToolsFind(target: TargetInfo, tool: String): Command? =
        findMsvcToolsFindTool(target, tool)?.toCommand()

    fun findMsvcToolsFindTool(target: TargetInfo, tool: String): Tool? {
        if (target.env != "msvc") {
            return null
        }
        return findMsvcToolProvider?.findToolWithEnv(target.fullArch, tool) { name -> getenv(name) }
    }

    fun autodetectWasiCompiler(rawTarget: String, clang: String): String {
        val path = getenv("WASI_SDK_PATH")
        if (path != null) {
            val targetClang = "$path/bin/$rawTarget-clang"
            val found = which(targetClang, null)
            if (found != null) {
                return found
            }
        }
        return clang
    }

    fun cmd(prog: String): Command {
        val c = Command.new(prog)
        for ((a, b) in env) {
            c.env(a, b)
        }
        return c
    }

    fun preferClang(): Boolean {
        val envVal = getenv("CARGO_ENCODED_RUSTFLAGS")
        return envVal?.contains("linker-plugin-lto") ?: false
    }

    fun rustcWrapperFallback(): String? {
        val validWrappers = setOf("sccache", "cachepot", "buildcache")
        val wrapper = getenv("RUSTC_WRAPPER") ?: return null
        val stem = wrapper.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
        return if (stem in validWrappers) wrapper else null
    }

    fun envTool(name: String): Triple<String, String?, List<String>>? {
        val toolStr = getenvWithTargetPrefixes(name).getOrNull()?.trim() ?: return null
        if (toolStr.isEmpty()) return null

        val exe = checkExe(toolStr)
        if (exe != null) {
            return Triple(exe, rustcWrapperFallback(), emptyList())
        }

        val knownWrappers =
            mutableSetOf(
                "ccache",
                "distcc",
                "sccache",
                "icecc",
                "cachepot",
                "buildcache",
            )
        getenv("CC_KNOWN_WRAPPER_CUSTOM")?.let { knownWrappers.add(it) }

        val parts = toolStr.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null

        val maybeWrapper = parts[0]
        val fileStem = maybeWrapper.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
        return if (fileStem in knownWrappers && parts.size > 1) {
            Triple(parts[1], maybeWrapper, parts.drop(2))
        } else {
            Triple(parts[0], null, parts.drop(1))
        }
    }

    fun getCppLinkStdlib(): Result<String?> {
        val std = cppLinkStdlib
        if (std != null) {
            return Result.success(std)
        }
        val fromEnv = getenvWithTargetPrefixes("CXXSTDLIB").getOrNull()
        if (fromEnv != null) {
            return Result.success(fromEnv.ifEmpty { null })
        }
        val target = getTarget().getOrElse { return Result.failure(it) }
        return Result.success(
            when {
                target.env == "msvc" -> null
                target.vendor == "apple" ||
                    target.os in setOf("freebsd", "openbsd", "aix", "wasi") ||
                    (target.os == "linux" && target.env == "ohos") -> "c++"
                target.os == "android" -> "c++_shared"
                else -> "stdc++"
            },
        )
    }

    fun prefixForTarget(target: String): String? {
        val cross = getenv("CROSS_COMPILE")?.trimEnd('-')
        if (cross != null) return cross

        val linker = getenv("RUSTC_LINKER")
        if (linker != null && linker.endsWith("-gcc")) {
            return linker.removeSuffix("-gcc")
        }

        return when (target) {
            "aarch64-pc-windows-gnullvm", "aarch64-uwp-windows-gnu" -> "aarch64-w64-mingw32"
            "aarch64-unknown-helenos" -> "aarch64-helenos"
            "aarch64-unknown-linux-gnu" -> "aarch64-linux-gnu"
            "aarch64-unknown-linux-musl" -> "aarch64-linux-musl"
            "aarch64-unknown-netbsd" -> "aarch64--netbsd"
            "arm-unknown-linux-gnueabi", "armv4t-unknown-linux-gnueabi",
            "armv5te-unknown-linux-gnueabi", "armv5te-unknown-linux-musleabi",
            "armv7-unknown-linux-gnueabi",
            -> "arm-linux-gnueabi"
            "armv5te-unknown-helenos-eabi" -> "arm-helenos"
            "arm-unknown-linux-gnueabihf", "armv7-unknown-linux-gnueabihf",
            "armv7neon-unknown-linux-gnueabihf", "thumbv7-unknown-linux-gnueabihf",
            "thumbv7neon-unknown-linux-gnueabihf",
            -> "arm-linux-gnueabihf"
            "arm-unknown-linux-musleabi" -> "arm-linux-musleabi"
            "arm-unknown-linux-musleabihf", "armv7-unknown-linux-musleabihf",
            "armv7neon-unknown-linux-musleabihf", "thumbv7-unknown-linux-musleabihf",
            "thumbv7neon-unknown-linux-musleabihf",
            -> "arm-linux-musleabihf"
            "arm-unknown-netbsd-eabi" -> "arm--netbsdelf-eabi"
            "armv6-unknown-netbsd-eabihf" -> "armv6--netbsdelf-eabihf"
            "armv7-unknown-netbsd-eabihf" -> "armv7--netbsdelf-eabihf"
            "hexagon-unknown-linux-musl" -> "hexagon-linux-musl"
            "i586-unknown-linux-musl", "i686-unknown-linux-musl" -> "musl"
            "i686-pc-windows-gnu", "i686-pc-windows-gnullvm", "i686-uwp-windows-gnu" -> "i686-w64-mingw32"
            "i686-unknown-helenos" -> "i686-helenos"
            "i686-unknown-linux-gnu" -> findWorkingGnuPrefix(listOf("i686-linux-gnu", "x86_64-linux-gnu"))
            "i686-unknown-netbsd" -> "i486--netbsdelf"
            "loongarch64-unknown-linux-gnu" -> "loongarch64-linux-gnu"
            "m68k-unknown-linux-gnu" -> "m68k-linux-gnu"
            "mips-unknown-linux-gnu" -> "mips-linux-gnu"
            "mips-unknown-linux-musl" -> "mips-linux-musl"
            "mipsel-unknown-linux-gnu" -> "mipsel-linux-gnu"
            "mipsel-unknown-linux-musl" -> "mipsel-linux-musl"
            "mips64-unknown-linux-gnuabi64" -> "mips64-linux-gnuabi64"
            "mips64el-unknown-linux-gnuabi64" -> "mips64el-linux-gnuabi64"
            "mipsisa32r6-unknown-linux-gnu" -> "mipsisa32r6-linux-gnu"
            "mipsisa32r6el-unknown-linux-gnu" -> "mipsisa32r6el-linux-gnu"
            "mipsisa64r6-unknown-linux-gnuabi64" -> "mipsisa64r6-linux-gnuabi64"
            "mipsisa64r6el-unknown-linux-gnuabi64" -> "mipsisa64r6el-linux-gnuabi64"
            "powerpc-unknown-helenos" -> "ppc-helenos"
            "powerpc-unknown-linux-gnu" -> "powerpc-linux-gnu"
            "powerpc-unknown-linux-gnuspe" -> "powerpc-linux-gnuspe"
            "powerpc-unknown-netbsd" -> "powerpc--netbsd"
            "powerpc64-unknown-linux-gnu" -> "powerpc64-linux-gnu"
            "powerpc64le-unknown-linux-gnu" -> "powerpc64le-linux-gnu"
            "riscv32i-unknown-none-elf", "riscv32im-unknown-none-elf",
            "riscv32imac-unknown-none-elf", "riscv32imafc-unknown-none-elf",
            "riscv32imac-unknown-xous-elf", "riscv32imc-unknown-none-elf",
            "riscv64gc-unknown-none-elf", "riscv64imac-unknown-none-elf",
            ->
                findWorkingGnuPrefix(listOf("riscv32-unknown-elf", "riscv64-unknown-elf", "riscv-none-embed"))
            "riscv32imac-esp-espidf", "riscv32imc-esp-espidf" -> "riscv32-esp-elf"
            "riscv64gc-unknown-linux-gnu", "riscv64a23-unknown-linux-gnu" -> "riscv64-linux-gnu"
            "riscv32gc-unknown-linux-gnu" -> "riscv32-linux-gnu"
            "riscv64gc-unknown-linux-musl" -> "riscv64-linux-musl"
            "riscv32gc-unknown-linux-musl" -> "riscv32-linux-musl"
            "riscv64gc-unknown-netbsd" -> "riscv64--netbsd"
            "s390x-unknown-linux-gnu" -> "s390x-linux-gnu"
            "sparc-unknown-linux-gnu" -> "sparc-linux-gnu"
            "sparc64-unknown-helenos" -> "sparc64-helenos"
            "sparc64-unknown-linux-gnu" -> "sparc64-linux-gnu"
            "sparc64-unknown-netbsd" -> "sparc64--netbsd"
            "sparcv9-sun-solaris" -> "sparcv9-sun-solaris"
            "armv7a-none-eabi", "armv7a-none-eabihf", "armebv7r-none-eabi",
            "armebv7r-none-eabihf", "armv7r-none-eabi", "armv7r-none-eabihf",
            "armv8r-none-eabihf", "thumbv6m-none-eabi", "thumbv7em-none-eabi",
            "thumbv7em-none-eabihf", "thumbv7m-none-eabi", "thumbv8m.base-none-eabi",
            "thumbv8m.main-none-eabi", "thumbv8m.main-none-eabihf",
            -> "arm-none-eabi"
            "x86_64-pc-windows-gnu", "x86_64-pc-windows-gnullvm", "x86_64-uwp-windows-gnu" -> "x86_64-w64-mingw32"
            "x86_64-rumprun-netbsd" -> "x86_64-rumprun-netbsd"
            "x86_64-unknown-helenos" -> "amd64-helenos"
            "x86_64-unknown-linux-gnu" -> findWorkingGnuPrefix(listOf("x86_64-linux-gnu"))
            "x86_64-unknown-linux-musl" -> findWorkingGnuPrefix(listOf("x86_64-linux-musl", "musl"))
            "x86_64-unknown-netbsd" -> "x86_64--netbsd"
            "xtensa-esp32-espidf", "xtensa-esp32-none-elf",
            "xtensa-esp32s2-espidf", "xtensa-esp32s2-none-elf",
            "xtensa-esp32s3-espidf", "xtensa-esp32s3-none-elf",
            -> "xtensa-esp-elf"
            else -> null
        }
    }

    fun findWorkingGnuPrefix(prefixes: List<String>): String? {
        val suffix = if (cpp) "-g++" else "-gcc"
        val ext = exeExtension()
        val path = getenv("PATH")
        if (path != null) {
            val paths = path.split(if (isWindows()) ';' else ':')
            for (p in paths) {
                for (prefix in prefixes) {
                    val candidate = "$p/$prefix$suffix$ext"
                    if (fileExists(candidate)) {
                        return prefix
                    }
                }
            }
        }
        return prefixes.firstOrNull()
    }

    fun getBaseCompiler(): Result<Tool> {
        val outDirStr = getOutDir().getOrNull()
        val comp = compiler
        if (comp != null) {
            return Result.success(
                Tool.new(comp, buildCache.cachedCompilerFamily, cargoOutput, outDirStr),
            )
        }

        val target = getTarget().getOrElse { return Result.failure(it) }
        val rawTarget = getRawTarget().getOrElse { return Result.failure(it) }

        val msvc = if (preferClangClOverMsvc) "clang-cl.exe" else "cl.exe"
        val envName = if (cpp) "CXX" else "CC"
        val gnu = if (cpp) "g++" else "gcc"
        val traditional = if (cpp) "c++" else "cc"
        val clang = if (cpp) "clang++" else "clang"

        val fallback = traditional
        val defaultComp =
            if (preferClang()) {
                which(clang, null) ?: fallback
            } else {
                fallback
            }

        val clExe = findMsvcToolsFindTool(target, msvc)

        val envToolRes = envTool(envName)
        if (envToolRes != null) {
            val (toolPath, wrapper, args) = envToolRes
            val t = Tool.withArgs(toolPath, args, buildCache.cachedCompilerFamily, cargoOutput, outDirStr)
            if (wrapper != null) {
                t.ccWrapperPath = wrapper
            }
            t.ccWrapperArgs.addAll(args)
            return Result.success(t)
        }

        if (target.os == "emscripten") {
            val emTool = if (cpp) "em++" else "emcc"
            return if (isWindows()) {
                val t = Tool.withFamily("cmd", ToolFamily.Clang(zigCc = false))
                t.args.add("/c")
                t.args.add("$emTool.bat")
                Result.success(t)
            } else {
                Result.success(Tool.new(emTool, buildCache.cachedCompilerFamily, cargoOutput, outDirStr))
            }
        }

        if (clExe != null) {
            return Result.success(clExe)
        }

        val crossPrefix = prefixForTarget(rawTarget)
        if (crossPrefix != null) {
            val tool = "$crossPrefix-$gnu"
            return Result.success(Tool.new(tool, buildCache.cachedCompilerFamily, cargoOutput, outDirStr))
        }

        return Result.success(Tool.new(defaultComp, buildCache.cachedCompilerFamily, cargoOutput, outDirStr))
    }

    fun getBaseArchiver(): Result<Pair<Command, String>> {
        val a = archiver
        if (a != null) {
            return Result.success(cmd(a) to a)
        }
        return getBaseArchiverVariant("AR", "ar")
    }

    fun getBaseArchiverVariant(envName: String, tool: String): Result<Pair<Command, String>> {
        val target = getTarget().getOrElse { return Result.failure(it) }
        val rawTarget = getRawTarget().getOrElse { return Result.failure(it) }

        val envToolRes = envTool(envName)
        if (envToolRes != null) {
            val (toolPath, _, args) = envToolRes
            val c = cmd(toolPath)
            c.args(args)
            return Result.success(c to toolPath)
        }

        if (target.env == "msvc") {
            val libTool = findMsvcToolsFindTool(target, "lib.exe")
            if (libTool != null) {
                return Result.success(libTool.toCommand() to libTool.path)
            }
            return Result.success(cmd("lib.exe") to "lib.exe")
        }

        val crossPrefix = prefixForTarget(rawTarget)
        val toolName = if (crossPrefix != null) "$crossPrefix-$tool" else tool
        return Result.success(cmd(toolName) to toolName)
    }

    fun tryGetArchiverAndFlags(): Result<Triple<Command, String, Boolean>> {
        val (baseCmd, name) = getBaseArchiver().getOrElse { return Result.failure(it) }
        var anyFlags = false
        val flags = envflags("ARFLAGS").getOrElse { return Result.failure(it) }
        if (flags != null) {
            anyFlags = true
            baseCmd.args(flags)
        }
        for (flag in arFlags) {
            anyFlags = true
            baseCmd.arg(flag)
        }
        return Result.success(Triple(baseCmd, name, anyFlags))
    }

    fun tryGetArchiver(): Result<Command> = tryGetArchiverAndFlags().map { it.first }

    fun getArchiver(): Command =
        tryGetArchiver().getOrElse { fail(it.message ?: "Failed to get archiver") }

    fun getBaseRanlib(): Result<Command> {
        val r = ranlib
        if (r != null) {
            return Result.success(cmd(r))
        }
        return getBaseArchiverVariant("RANLIB", "ranlib").map { it.first }
    }

    fun tryGetRanlib(): Result<Command> {
        val c = getBaseRanlib().getOrElse { return Result.failure(it) }
        val flags = envflags("RANLIBFLAGS").getOrElse { return Result.failure(it) }
        if (flags != null) {
            c.args(flags)
        }
        return Result.success(c)
    }

    fun getRanlib(): Command =
        tryGetRanlib().getOrElse { fail(it.message ?: "Failed to get ranlib") }

    fun addDefaultFlags(
        cmd: Tool,
        target: TargetInfo,
        optLevel: String,
    ): Result<Unit> {
        when (cmd.family) {
            is ToolFamily.Msvc -> {
                cmd.pushCcArg("-nologo")
                val crtFlag =
                    when (staticCrt) {
                        true -> "-MT"
                        false -> "-MD"
                        null -> {
                            val feat = getenv("CARGO_CFG_TARGET_FEATURE").orEmpty()
                            if (feat.contains("crt-static")) "-MT" else "-MD"
                        }
                    }
                cmd.pushCcArg(crtFlag)
                when (optLevel) {
                    "z", "s", "1" -> cmd.pushOptUnlessDuplicate("-O1")
                    "2", "3" -> cmd.pushOptUnlessDuplicate("-O2")
                }
            }
            ToolFamily.Gnu, is ToolFamily.Clang -> {
                if (optLevel == "z" && !cmd.isLikeClang()) {
                    cmd.pushOptUnlessDuplicate("-Os")
                } else {
                    cmd.pushOptUnlessDuplicate("-O$optLevel")
                }

                if (cmd.isLikeClang() && target.os == "android") {
                    cmd.pushOptUnlessDuplicate("-DANDROID")
                }

                if (target.os !in setOf("ios", "watchos", "tvos", "visionos")) {
                    cmd.pushCcArg("-ffunction-sections")
                    cmd.pushCcArg("-fdata-sections")
                }

                val defaultPic =
                    target.os !in setOf("windows", "none", "uefi", "vita") &&
                        target.arch !in setOf("wasm32", "wasm64")
                if (pic ?: defaultPic) {
                    cmd.pushCcArg("-fPIC")
                    if ((target.os == "linux" || target.os == "android") && (usePlt ?: true).not()) {
                        cmd.pushCcArg("-fno-plt")
                    }
                }
                if (target.arch in setOf("wasm32", "wasm64")) {
                    cmd.pushCcArg("-fno-exceptions")
                }
            }
        }
        return Result.success(Unit)
    }

    fun addInheritedRustflags(cmd: Tool, target: TargetInfo): Result<Unit> {
        val envVal = getenv("CARGO_ENCODED_RUSTFLAGS") ?: return Result.success(Unit)
        val codegenFlags = RustcCodegenFlags.parse(envVal)
        codegenFlags.ccFlags(this, cmd, target)
        return Result.success(Unit)
    }

    fun appleFlags(cmd: Tool): Result<Unit> {
        val target = getTarget().getOrElse { return Result.failure(it) }
        if (cmd.isLikeGnu()) {
            val arch = mapDarwinTargetFromRustToCompilerArchitecture(target)
            cmd.args.add("-arch")
            cmd.args.add(arch)
        }

        if (cmd.isLikeGnu() || !(target.os == "visionos" || target.env == "macabi")) {
            val minVer = appleDeploymentTarget(target)
            cmd.args.add(target.appleVersionFlag(minVer))
        }

        if (cmd.isXctoolchainClang() || target.os != "macos") {
            cargoOutput.printMetadata("Detecting ${target.os} SDK path for ${target.appleSdkName()}")
            val sdkPath = appleSdkRoot(target).getOrElse { return Result.failure(it) }
            cmd.args.add("-isysroot")
            cmd.args.add(sdkPath)
            cmd.env.add("SDKROOT" to sdkPath)

            if (target.env == "macabi") {
                val iosSupport = "$sdkPath/System/iOSSupport"
                cmd.args.addAll(
                    listOf(
                        "-isystem",
                        "$iosSupport/usr/include",
                        "-iframework",
                        "$iosSupport/System/Library/Frameworks",
                        "-L$iosSupport/usr/lib",
                        "-F$iosSupport/System/Library/Frameworks",
                    ),
                )
            }
        }

        return Result.success(Unit)
    }

    fun tryGetCompiler(): Result<Tool> {
        val optLevel = getOptLevel().getOrElse { return Result.failure(it) }
        val target = getTarget().getOrElse { return Result.failure(it) }
        val cmd = getBaseCompiler().getOrElse { return Result.failure(it) }

        if (cmd.family is ToolFamily.Msvc && !cmd.family.clangCl) {
            cmd.env.add("VSLANG" to "1033")
        } else {
            cmd.env.add("LC_ALL" to "C")
        }

        val noDefaults = noDefaultFlags || getenvBoolean("CRATE_CC_NO_DEFAULTS")
        if (!noDefaults) {
            addDefaultFlags(cmd, target, optLevel).getOrElse { return Result.failure(it) }
        }

        val s = std
        if (s != null) {
            val separator = if (cmd.family is ToolFamily.Msvc) ':' else '='
            cmd.pushCcArg("-std$separator$s")
        }

        for (dir in includeDirectories) {
            cmd.args.add("-I")
            cmd.args.add(dir)
        }

        if (warningsIntoErrors) {
            cmd.pushCcArg(cmd.family.warningsToErrorsFlag())
        }

        val envflags = envflags(if (cpp) "CXXFLAGS" else "CFLAGS").getOrElse { return Result.failure(it) }
        when (warnings) {
            true -> cmd.pushCcArg(cmd.family.warningsFlags())
            false -> cmd.pushCcArg(cmd.family.warningsSuppressionFlags())
            null -> if (envflags == null) cmd.pushCcArg(cmd.family.warningsFlags())
        }

        if (extraWarnings ?: (envflags == null)) {
            val extra = cmd.family.extraWarningsFlags()
            if (extra != null) {
                cmd.pushCcArg(extra)
            }
        }

        if (inheritRustflags) {
            addInheritedRustflags(cmd, target).getOrElse { return Result.failure(it) }
        }

        for (flag in flags) {
            cmd.args.add(flag)
        }

        for (flag in flagsSupported) {
            if (isFlagSupportedInner(flag, cmd, target).getOrDefault(false)) {
                cmd.pushCcArg(flag)
            }
        }

        for ((k, v) in definitions) {
            if (v != null) {
                cmd.args.add("-D$k=$v")
            } else {
                cmd.args.add("-D$k")
            }
        }

        if (envflags != null) {
            for (arg in envflags) {
                cmd.pushCcArg(arg)
            }
        }

        return Result.success(cmd)
    }

    fun getCompiler(): Tool =
        tryGetCompiler().getOrElse { fail(it.message ?: "Failed to get compiler") }

    fun isFlagSupported(flag: String): Result<Boolean> {
        val comp = getBaseCompiler().getOrElse { return Result.failure(it) }
        val target = getTarget().getOrElse { return Result.failure(it) }
        return isFlagSupportedInner(flag, comp, target)
    }

    fun ensureCheckFile(): Result<String> =
        runCatching {
            val outDir = getOutDir().getOrThrow()
            val src =
                when {
                    cuda -> {
                        check(cpp)
                        "$outDir/flag_check.cu"
                    }
                    cpp -> "$outDir/flag_check.cpp"
                    else -> "$outDir/flag_check.c"
                }
            if (!fileExists(src)) {
                createNamed(src).getOrThrow().use { handle ->
                    handle.write("int main(void) { return 0; }".encodeToByteArray())
                    handle.flush()
                }
            }
            src
        }

    fun isFlagSupportedInner(
        flag: String,
        tool: Tool,
        target: TargetInfo,
    ): Result<Boolean> {
        val compilerFlag = CompilerFlag(tool.path(), flag)
        val cached = buildCache.knownFlagSupportStatusCache[compilerFlag]
        if (cached != null) {
            return Result.success(cached)
        }

        val outDir = getOutDir().getOrElse { return Result.failure(it) }
        val src = ensureCheckFile().getOrElse { return Result.failure(it) }
        val obj = "$outDir/flag_check"

        val compiler =
            run {
                val cfg = new()
                cfg
                    .flag(flag)
                    .compiler(tool.path())
                    .cargoMetadata(cargoOutput.metadata)
                    .optLevel(0u)
                    .debug(false)
                    .cpp(cpp)
                    .cuda(cuda)
                    .inheritRustflags(false)
                    .emitRerunIfEnvChanged(emitRerunIfEnvChanged)
                this.target?.let { cfg.target(it) }
                this.host?.let { cfg.host(it) }
                cfg.tryGetCompiler().getOrElse { return Result.failure(it) }
            }

        if (compiler.family.verboseStderr()) {
            compiler.removeArg("-v")
        }
        if (compiler.isLikeClang()) {
            compiler.pushCcArg("-Wno-unused-command-line-argument")
        }

        val cmd = compiler.toCommand()
        commandAddOutputFile(
            cmd,
            obj,
            CmdAddOutputFileArgs(
                cuda = cuda,
                isAssemblerMsvc = false,
                msvc = compiler.isLikeMsvc(),
                clang = compiler.isLikeClang(),
                gnu = compiler.isLikeGnu(),
                isAsm = false,
                isArm = isArm(target),
            ),
        )

        cmd.arg("-c")
        if (compiler.supportsPathDelimiter()) {
            cmd.arg("--")
        }
        cmd.arg(src)

        if (compiler.isLikeMsvc()) {
            for ((k, v) in tool.env) {
                if (k == "LIB") {
                    cmd.env("LIB", v)
                    break
                }
            }
        }

        cmd.currentDir(outDir)
        val outRes = spawnAndWaitForOutput(cmd, cargoOutput)
        val isSupported =
            if (outRes.isSuccess) {
                val out = outRes.getOrThrow()
                out.status.success && out.stderr.isEmpty()
            } else {
                false
            }

        buildCache.knownFlagSupportStatusCache[compilerFlag] = isSupported
        return Result.success(isSupported)
    }

    fun msvcMacroAssembler(): Result<Command> {
        val target = getTarget().getOrElse { return Result.failure(it) }
        val tool =
            when (target.arch) {
                "x86_64" -> "ml64.exe"
                "arm" -> "armasm.exe"
                "aarch64", "arm64ec" -> "armasm64.exe"
                else -> "ml.exe"
            }
        val cmd = findMsvcToolsFind(target, tool) ?: cmd(tool)
        cmd.arg("-nologo")
        for (dir in includeDirectories) {
            cmd.arg("-I").arg(dir)
        }
        if (isArm(target)) {
            if (getDebug()) {
                cmd.arg("-g")
            }
            if (target.arch == "arm64ec") {
                cmd.args(listOf("-machine", "ARM64EC"))
            }
            for ((key, value) in definitions) {
                cmd.arg("-PreDefine")
                if (value != null) {
                    val intVal = value.toIntOrNull()
                    if (intVal != null) {
                        cmd.arg("$key SETA $intVal")
                    } else if (value.startsWith('"') && value.endsWith('"')) {
                        cmd.arg("$key SETS $value")
                    } else {
                        cmd.arg("$key SETS \"$value\"")
                    }
                } else {
                    cmd.arg("$key SETL {TRUE}")
                }
            }
        } else {
            if (getDebug()) {
                cmd.arg("-Zi")
            }
            for ((key, value) in definitions) {
                if (value != null) {
                    cmd.arg("-D$key=$value")
                } else {
                    cmd.arg("-D$key")
                }
            }
        }
        if (target.arch == "x86") {
            cmd.arg("-safeseh")
        }
        return Result.success(cmd)
    }

    fun assembleProgressive(dst: String, objs: List<String>): Result<Unit> {
        val target = getTarget().getOrElse { return Result.failure(it) }
        val (cmd, program, anyFlags) = tryGetArchiverAndFlags().getOrElse { return Result.failure(it) }

        if (target.env == "msvc" && !program.contains("llvm-ar")) {
            cmd.arg("-out:$dst")
            if (!anyFlags) {
                cmd.arg("-nologo")
            }
            if (fileExists(dst)) {
                cmd.arg(dst)
            }
            cmd.args(objs)
            return run(cmd, cargoOutput)
        } else {
            cmd.env("ZERO_AR_DATE", "1")
            cmd.arg("cq").arg(dst).args(objs)
            return run(cmd, cargoOutput)
        }
    }

    fun assemble(libName: String, dst: String, objs: List<Object>): Result<Unit> {
        removeFile(dst)
        val allObjs = objs.map { it.dst } + objects
        for (chunk in allObjs.chunked(100)) {
            assembleProgressive(dst, chunk).getOrElse { return Result.failure(it) }
        }

        if (cuda && cudaFileCount() > 0) {
            val outDir = getOutDir().getOrElse { return Result.failure(it) }
            val dlink = "$outDir/${libName}_dlink.o"
            val nvcc = getCompiler().toCommand()
            nvcc
                .arg("--device-link")
                .arg("-o")
                .arg(dlink)
                .arg(dst)
            run(nvcc, cargoOutput).getOrElse { return Result.failure(it) }
            assembleProgressive(dst, listOf(dlink)).getOrElse { return Result.failure(it) }
        }

        val target = getTarget().getOrElse { return Result.failure(it) }
        if (target.env == "msvc") {
            val libDst = dst.substringBeforeLast('/') + "/$libName.lib"
            removeFile(libDst)
        } else {
            val ar = tryGetArchiver().getOrElse { return Result.failure(it) }
            ar.arg("s").arg(dst)
            run(ar, cargoOutput).getOrElse { return Result.failure(it) }
        }
        return Result.success(Unit)
    }

    fun createCompileObjectCmd(obj: Object): Result<Command> {
        val asmExt = AsmFileExt.fromPath(obj.src)
        val target = getTarget().getOrElse { return Result.failure(it) }
        val msvc = target.env == "msvc"
        val compiler = tryGetCompiler().getOrElse { return Result.failure(it) }

        val isAssemblerMsvc = msvc && asmExt == AsmFileExt.DotAsm
        val cmd =
            if (isAssemblerMsvc) {
                msvcMacroAssembler().getOrElse { return Result.failure(it) }
            } else {
                val c = compiler.toCommand()
                for ((a, b) in env) {
                    c.env(a, b)
                }
                c
            }

        commandAddOutputFile(
            cmd,
            obj.dst,
            CmdAddOutputFileArgs(
                cuda = cuda,
                isAssemblerMsvc = isAssemblerMsvc,
                msvc = compiler.isLikeMsvc(),
                clang = compiler.isLikeClang(),
                gnu = compiler.isLikeGnu(),
                isAsm = asmExt != null,
                isArm = isArm(target),
            ),
        )

        if (!isAssemblerMsvc) {
            cmd.arg("-c")
        }

        if (compiler.supportsPathDelimiter() && asmExt == null) {
            cmd.arg("--")
        }

        cmd.arg(obj.src)

        return Result.success(cmd)
    }

    fun compileObjects(objs: List<Object>): Result<Unit> {
        checkDisabled().getOrElse { return Result.failure(it) }
        if (objs.size > 1) {
            val cmdIter = objs.asSequence().map { createCompileObjectCmd(it) }.iterator()
            return runCommandsInParallel(cargoOutput, cmdIter)
        }

        for (obj in objs) {
            val cmd = createCompileObjectCmd(obj).getOrElse { return Result.failure(it) }
            run(cmd, cargoOutput).getOrElse { return Result.failure(it) }
        }
        return Result.success(Unit)
    }

    fun tryCompileIntermediates(): Result<List<String>> {
        val dst = getOutDir().getOrElse { return Result.failure(it) }
        val objects = objectsFromFiles(files, dst).getOrElse { return Result.failure(it) }
        compileObjects(objects).getOrElse { return Result.failure(it) }
        return Result.success(objects.map { it.dst })
    }

    fun compileIntermediates(): List<String> =
        tryCompileIntermediates().getOrElse { fail(it.message ?: "Failed to compile intermediates") }

    fun tryCompile(output: String): Result<Unit> {
        if (output.contains('/') || output.contains('\\') || output.isEmpty()) {
            return Result.failure(
                Error.new(
                    ErrorKind.InvalidArgument,
                    "argument of `compile` must be a single normal path component",
                ),
            )
        }

        val (libName, gnuLibName) =
            if (output.startsWith("lib") && output.endsWith(".a")) {
                output.substring(3, output.length - 2) to output
            } else {
                output to "lib$output.a"
            }

        val dst = getOutDir().getOrElse { return Result.failure(it) }
        val objects = objectsFromFiles(files, dst).getOrElse { return Result.failure(it) }
        compileObjects(objects).getOrElse { return Result.failure(it) }
        assemble(libName, "$dst/$gnuLibName", objects).getOrElse { return Result.failure(it) }

        val target = getTarget().getOrElse { return Result.failure(it) }
        if (linkLibModifiers.isEmpty()) {
            cargoOutput.printMetadata("cargo:rustc-link-lib=static=$libName")
        } else {
            cargoOutput.printMetadata(
                "cargo:rustc-link-lib=static:${linkLibModifiers.joinToString(",")}=$libName",
            )
        }
        cargoOutput.printMetadata("cargo:rustc-link-search=native=$dst")

        if (cpp) {
            val stdlib = getCppLinkStdlib().getOrElse { return Result.failure(it) }
            if (stdlib != null) {
                if (cppLinkStdlibStatic) {
                    cargoOutput.printMetadata("cargo:rustc-link-lib=static=$stdlib")
                } else {
                    cargoOutput.printMetadata("cargo:rustc-link-lib=$stdlib")
                }
            }
            if (target.arch == "wasm32" && target.os == "wasi") {
                val wasiSys = wasiSysroot().getOrNull()
                if (wasiSys != null) {
                    val rawTarget = getRawTarget().getOrElse { return Result.failure(it) }
                    cargoOutput.printMetadata(
                        "cargo:rustc-flags=-L $wasiSys/lib/$rawTarget -lstatic=c++ -lstatic=c++abi",
                    )
                }
            }
        }

        return Result.success(Unit)
    }

    fun compile(output: String) {
        tryCompile(output).getOrElse { fail(it.message ?: "Failed to compile") }
    }

    fun tryExpand(): Result<ByteArray> {
        val compiler = tryGetCompiler().getOrElse { return Result.failure(it) }
        val cmd = compiler.toCommand()
        for ((a, b) in env) {
            cmd.env(a, b)
        }
        cmd.arg("-E")
        check(files.size <= 1) { "Expand may only be called for a single file" }

        val isAsm = files.any { AsmFileExt.fromPath(it) != null }
        if (compiler.family is ToolFamily.Msvc && compiler.family.clangCl && !isAsm) {
            cmd.arg("--")
        }
        cmd.args(files)
        return runOutput(cmd, cargoOutput)
    }

    fun expand(): ByteArray =
        tryExpand().getOrElse { fail(it.message ?: "Failed to expand") }
}
