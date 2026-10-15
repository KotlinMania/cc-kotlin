// port-lint: source src/target/parser.rs
package io.github.kotlinmania.cc.target

import io.github.kotlinmania.cc.Error
import io.github.kotlinmania.cc.ErrorKind
import io.github.kotlinmania.cc.getEnv
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal data class TargetInfoParserInner(
    val fullArch: String,
    val arch: String,
    val vendor: String,
    val os: String,
    val env: String,
    val abi: String,
) {
    fun toTargetInfo(): TargetInfo =
        TargetInfo(
            fullArch = fullArch,
            arch = arch,
            vendor = vendor,
            os = os,
            env = env,
            abi = abi,
        )

    companion object {
        fun fromCargoEnvironmentVariables(): Result<TargetInfoParserInner> =
            runCatching {
                val targetName =
                    getEnv("TARGET") ?: throw Error.new(
                        ErrorKind.EnvVarNotFound,
                        "failed reading TARGET: environment variable not found",
                    )

                val dashIndex = targetName.indexOf('-')
                if (dashIndex == -1) {
                    throw Error.new(
                        ErrorKind.InvalidTarget,
                        "target `$targetName` only had a single component (at least two required)",
                    )
                }
                val fullArch = targetName.substring(0, dashIndex)

                fun cargoEnv(
                    name: String,
                    fallback: String?,
                ): String {
                    val envVal = getEnv(name)
                    if (envVal != null) return envVal
                    if (fallback != null) return fallback
                    throw Error.new(
                        ErrorKind.EnvVarNotFound,
                        "did not find fallback information for target `$targetName`, and failed reading $name: environment variable not found",
                    )
                }

                val fallbackTarget = TargetInfo.fromRustcTarget(targetName).getOrNull()
                val arch = cargoEnv("CARGO_CFG_TARGET_ARCH", fallbackTarget?.arch)
                val vendor = cargoEnv("CARGO_CFG_TARGET_VENDOR", fallbackTarget?.vendor)
                val os = cargoEnv("CARGO_CFG_TARGET_OS", fallbackTarget?.os)
                var env = cargoEnv("CARGO_CFG_TARGET_ENV", fallbackTarget?.env)
                var abi = runCatching { cargoEnv("CARGO_CFG_TARGET_ABI", fallbackTarget?.abi) }.getOrDefault("")

                if (abi == "macabi" || abi == "sim") {
                    env = abi
                    abi = ""
                }

                TargetInfoParserInner(
                    fullArch = fullArch,
                    arch = arch,
                    vendor = vendor,
                    os = os,
                    env = env,
                    abi = abi,
                )
            }
    }
}

/**
 * Parser for [TargetInfo], contains cached information.
 */
class TargetInfoParser {
    private class CachedResult(
        val value: Result<TargetInfoParserInner>,
    )

    @OptIn(ExperimentalAtomicApi::class)
    private val cached = AtomicReference<CachedResult?>(null)

    @OptIn(ExperimentalAtomicApi::class)
    fun parseFromCargoEnvironmentVariables(): Result<TargetInfo> {
        val current = cached.load()
        val innerResult =
            if (current != null) {
                current.value
            } else {
                val computed = TargetInfoParserInner.fromCargoEnvironmentVariables()
                val newEntry = CachedResult(computed)
                if (cached.compareAndSet(null, newEntry)) {
                    computed
                } else {
                    cached.load()!!.value
                }
            }
        return innerResult.map { it.toTargetInfo() }
    }
}

/**
 * Parse the full architecture in the target name into the simpler
 * `cfg(target_arch = "...")` that `rustc` exposes.
 */
internal fun parseArch(fullArch: String): String? =
    when {
        fullArch.startsWith("mipsisa32r6") -> "mips32r6"
        fullArch.startsWith("mipsisa64r6") -> "mips64r6"
        fullArch.startsWith("mips64") -> "mips64"
        fullArch.startsWith("mips") -> "mips"
        fullArch.startsWith("loongarch64") -> "loongarch64"
        fullArch.startsWith("loongarch32") -> "loongarch32"
        fullArch.startsWith("powerpc64") -> "powerpc64"
        fullArch.startsWith("powerpc") -> "powerpc"
        fullArch.startsWith("ppc64") -> "powerpc64"
        fullArch.startsWith("ppc") -> "powerpc"
        fullArch.startsWith("x86_64") -> "x86_64"
        fullArch.startsWith("i") && fullArch.endsWith("86") -> "x86"
        fullArch == "arm64ec" -> "arm64ec"
        fullArch.startsWith("aarch64") -> "aarch64"
        fullArch.startsWith("arm64") -> "aarch64"
        fullArch.startsWith("arm") -> "arm"
        fullArch.startsWith("thumb") -> "arm"
        fullArch.startsWith("riscv64") -> "riscv64"
        fullArch.startsWith("riscv32") -> "riscv32"
        fullArch.startsWith("wasm64") -> "wasm64"
        fullArch.startsWith("wasm32") -> "wasm32"
        fullArch == "asmjs" -> "wasm32"
        fullArch.startsWith("nvptx64") -> "nvptx64"
        fullArch.startsWith("nvptx") -> "nvptx"
        fullArch.startsWith("bpf") -> "bpf"
        fullArch.startsWith("pulley64") -> "pulley64"
        fullArch.startsWith("pulley32") -> "pulley32"
        fullArch.startsWith("clever") -> "clever"
        fullArch == "sparc" || fullArch == "sparcv7" || fullArch == "sparcv8" -> "sparc"
        fullArch == "sparc64" || fullArch == "sparcv9" -> "sparc64"
        fullArch == "amdgcn" -> "amdgpu"
        fullArch == "avr" -> "avr"
        fullArch == "csky" -> "csky"
        fullArch == "hexagon" -> "hexagon"
        fullArch == "m68k" -> "m68k"
        fullArch == "msp430" -> "msp430"
        fullArch == "r600" -> "r600"
        fullArch == "s390x" -> "s390x"
        fullArch == "xtensa" -> "xtensa"
        fullArch.startsWith("alpha") -> "alpha"
        fullArch == "hppa" -> "hppa"
        fullArch.startsWith("sh") -> "sh"
        else -> null
    }

/**
 * Parse environment and ABI from the last component of the target name.
 */
internal fun parseEnvAbi(lastComponent: String): Pair<String, String>? =
    when {
        lastComponent.startsWith("gnu") -> {
            val abi = lastComponent.removePrefix("gnu").removePrefix("_")
            "gnu" to abi
        }
        lastComponent.startsWith("musl") -> {
            "musl" to lastComponent.removePrefix("musl")
        }
        lastComponent.startsWith("uclibc") -> {
            "uclibc" to lastComponent.removePrefix("uclibc")
        }
        lastComponent.startsWith("newlib") -> {
            "newlib" to lastComponent.removePrefix("newlib")
        }
        lastComponent == "msvc" -> "msvc" to ""
        lastComponent == "ohos" -> "ohos" to ""
        lastComponent == "qnx700" -> "nto70" to ""
        lastComponent == "qnx710_iosock" -> "nto71_iosock" to ""
        lastComponent == "qnx710" -> "nto71" to ""
        lastComponent == "qnx800" -> "nto80" to ""
        lastComponent == "sgx" -> "sgx" to ""
        lastComponent == "threads" -> "threads" to ""
        lastComponent == "mlibc" -> "mlibc" to ""
        lastComponent == "abi64" -> "" to "abi64"
        lastComponent == "abiv2" -> "" to "spe"
        lastComponent == "eabi" -> "" to "eabi"
        lastComponent == "eabihf" -> "" to "eabihf"
        lastComponent == "macabi" -> "macabi" to ""
        lastComponent == "sim" -> "sim" to ""
        lastComponent == "softfloat" -> "" to "softfloat"
        lastComponent == "spe" -> "" to "spe"
        lastComponent == "x32" -> "" to "x32"
        lastComponent == "elf" -> "" to ""
        lastComponent == "freestanding" -> "" to ""
        else -> null
    }

private val powerpcElfV1Targets =
    setOf(
        "powerpc64-unknown-linux-gnu",
        "powerpc64-wrs-vxworks",
    )

private val powerpcElfV2Targets =
    setOf(
        "powerpc64-unknown-freebsd",
        "powerpc64-unknown-linux-musl",
        "powerpc64-unknown-openbsd",
        "powerpc64le-unknown-freebsd",
        "powerpc64le-unknown-linux-gnu",
        "powerpc64le-unknown-linux-musl",
    )

fun TargetInfo.Companion.fromRustcTarget(target: String): Result<TargetInfo> =
    runCatching {
        if (target == "x86_64-unknown-linux-none") {
            return@runCatching TargetInfo(
                fullArch = "x86_64",
                arch = "x86_64",
                vendor = "unknown",
                os = "linux",
                env = "",
                abi = "",
            )
        }

        if (target == "armv7a-vex-v5") {
            return@runCatching TargetInfo(
                fullArch = "armv7a",
                arch = "arm",
                vendor = "vex",
                os = "vexos",
                env = "v5",
                abi = "eabihf",
            )
        }

        val components = target.split('-')
        if (components.isEmpty() || components[0].isEmpty()) {
            throw Error.new(
                ErrorKind.InvalidTarget,
                "target was empty",
            )
        }

        val fullArch = components[0]
        val arch =
            parseArch(fullArch) ?: throw Error.new(
                ErrorKind.UnknownTarget,
                "target `$target` had an unknown architecture",
            )

        val remaining = components.drop(1)
        var vendor: String
        var os: String
        var env: String
        var abi: String

        when (remaining.size) {
            0 ->
                throw Error.new(
                    ErrorKind.InvalidTarget,
                    "target `$target` must have at least two components",
                )
            1 -> {
                vendor = "unknown"
                os = remaining[0]
                env = ""
                abi = ""
            }
            2 -> {
                val vendorOrOs = remaining[0]
                val osOrEnvAbi = remaining[1]
                val parsed = parseEnvAbi(osOrEnvAbi)
                if (parsed != null) {
                    vendor = "unknown"
                    os = vendorOrOs
                    env = parsed.first
                    abi = parsed.second
                } else {
                    vendor = vendorOrOs
                    os = osOrEnvAbi
                    env = ""
                    abi = ""
                }
            }
            3 -> {
                val vendorComponent = remaining[0]
                val osComponent = remaining[1]
                val envAbi = remaining[2]
                val parsed =
                    parseEnvAbi(envAbi) ?: throw Error.new(
                        ErrorKind.UnknownTarget,
                        "unknown environment/ABI `$envAbi` in target `$target`",
                    )
                vendor = vendorComponent
                os = osComponent
                env = parsed.first
                abi = parsed.second
            }
            else ->
                throw Error.new(
                    ErrorKind.InvalidTarget,
                    "too many components in target `$target`",
                )
        }

        if (fullArch.startsWith("riscv32e")) {
            abi = "ilp32e"
        }

        when (os) {
            "3ds", "rtems", "espidf" -> env = "newlib"
            "vxworks" -> env = "gnu"
            "redox" -> env = "relibc"
            "aix" -> abi = "vec-extabi"
        }

        when (target) {
            "i386-apple-ios", "x86_64-apple-ios", "x86_64-apple-tvos" -> env = "sim"
            "mips64-openwrt-linux-musl" -> abi = "abi64"
            "armv6-unknown-freebsd", "armv6k-nintendo-3ds", "armv7-unknown-freebsd" -> abi = "eabihf"
            "armv7-unknown-linux-ohos", "armv7-unknown-trusty" -> abi = "eabi"
        }

        os =
            when {
                os == "3ds" || os == "switch" -> "horizon"
                os == "darwin" -> "macos"
                os.startsWith("wasi") -> {
                    env = os.removePrefix("wasi")
                    "wasi"
                }
                os == "androideabi" -> {
                    abi = "eabi"
                    "android"
                }
                else -> os
            }

        vendor =
            when {
                vendor.startsWith("esp") -> "espressif"
                vendor == "linux" && (os == "android" || os == "androideabi") -> "unknown"
                vendor == "wali" -> "unknown"
                vendor == "lynx" -> "unknown"
                else -> vendor
            }

        if (vendor == "fortanix") {
            abi = "fortanix"
        }
        if (vendor == "uwp") {
            abi = "uwp"
        }
        if (target in powerpcElfV1Targets) {
            abi = "elfv1"
        }
        if (target in powerpcElfV2Targets) {
            abi = "elfv2"
        }
        if (abi == "asan") {
            abi = ""
        }

        TargetInfo(
            fullArch = fullArch,
            arch = arch,
            vendor = vendor,
            os = os,
            env = env,
            abi = abi,
        )
    }
