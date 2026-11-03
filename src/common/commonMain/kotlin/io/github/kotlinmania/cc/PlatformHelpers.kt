package io.github.kotlinmania.cc

import io.github.kotlinmania.cc.target.TargetInfo
import kotlin.concurrent.atomics.AtomicInt

val NEW_STANDALONE_ANDROID_COMPILERS: List<String> =
    listOf(
        "aarch64-linux-android21-clang",
        "armv7a-linux-androideabi16-clang",
        "i686-linux-android16-clang",
        "x86_64-linux-android21-clang",
    )

fun androidClangCompilerUsesTargetArgInternally(clangPath: String): Boolean {
    val filename = clangPath.substringAfterLast('/').substringAfterLast('\\')
    val idx = filename.lastIndexOf('-')
    if (idx != -1) {
        return filename.substring(0, idx).contains("android")
    }
    return false
}

fun isLlvmMingwWrapper(clangPath: String): Boolean {
    val filename = clangPath.substringAfterLast('/').substringAfterLast('\\')
    return filename.endsWith("-w64-mingw32-clang") || filename.endsWith("-w64-mingw32-clang++")
}

fun autodetectAndroidCompiler(
    rawTarget: String,
    gnu: String,
    clang: String,
): String {
    val newClangKey =
        when (rawTarget) {
            "aarch64-linux-android" -> "aarch64"
            "armv7-linux-androideabi" -> "armv7a"
            "i686-linux-android" -> "i686"
            "x86_64-linux-android" -> "x86_64"
            else -> null
        }

    val newClang =
        newClangKey?.let { key ->
            NEW_STANDALONE_ANDROID_COMPILERS.firstOrNull { it.startsWith(key) }
        }

    if (newClang != null) {
        if (Command.new(newClang).output().isSuccess) {
            return newClang
        }
    }

    val target =
        rawTarget
            .replace("armv7neon", "arm")
            .replace("armv7", "arm")
            .replace("thumbv7neon", "arm")
            .replace("thumbv7", "arm")
    val gnuCompiler = "$target-$gnu"
    val clangCompiler = "$target-$clang"
    val clangCompilerCmd = "$target-$clang.cmd"

    return if (Command.new(gnuCompiler).output().isSuccess) {
        gnuCompiler
    } else if (isWindows() && Command.new(clangCompilerCmd).output().isSuccess) {
        clangCompilerCmd
    } else {
        clangCompiler
    }
}

fun mapDarwinTargetFromRustToCompilerArchitecture(target: TargetInfo): String =
    when (target.fullArch) {
        "aarch64" -> "arm64"
        "arm64_32" -> "arm64_32"
        "arm64e" -> "arm64e"
        "armv7k" -> "armv7k"
        "armv7s" -> "armv7s"
        "i386" -> "i386"
        "i686" -> "i386"
        "powerpc" -> "ppc"
        "powerpc64" -> "ppc64"
        "x86_64" -> "x86_64"
        "x86_64h" -> "x86_64h"
        else -> target.fullArch
    }

fun isArm(target: TargetInfo): Boolean =
    target.arch == "aarch64" || target.arch == "arm64ec" || target.arch == "arm"

enum class AsmFileExt {
    DotAsm,
    DotS,
    ;

    companion object {
        fun fromPath(file: String): AsmFileExt? {
            val ext = file.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "asm" -> DotAsm
                "s" -> DotS
                else -> null
            }
        }
    }
}

private val disabledCache = AtomicInt(0)

fun computeIsDisabled(): Boolean {
    val envVal = getEnv("CC_FORCE_DISABLE") ?: return false
    return when (envVal) {
        "0", "false", "no" -> false
        else -> true
    }
}

fun isDisabled(): Boolean {
    val current = disabledCache.load()
    if (current == 2) return true
    if (current == 1) return false

    val truth = computeIsDisabled()
    val encoded = if (truth) 2 else 1
    disabledCache.compareAndSet(0, encoded)
    return truth
}

fun checkDisabled(): Result<Unit> {
    if (isDisabled()) {
        return Result.failure(
            Error.new(
                ErrorKind.Disabled,
                "the `cc` crate's functionality has been disabled by the `CC_FORCE_DISABLE` environment variable.",
            ),
        )
    }
    return Result.success(Unit)
}

fun checkExe(exe: String): String? {
    if (fileExists(exe)) return exe
    val ext = exeExtension()
    if (ext.isNotEmpty()) {
        val withExt = if (exe.endsWith(ext)) exe else "$exe$ext"
        if (fileExists(withExt)) return withExt
    }
    return null
}

fun fail(message: String): Nothing {
    error("error occurred in cc-rs: $message")
}
