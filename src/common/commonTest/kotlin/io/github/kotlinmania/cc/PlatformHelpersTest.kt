// port-lint: tests src/lib.rs
package io.github.kotlinmania.cc

import io.github.kotlinmania.cc.target.TargetInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlatformHelpersTest {
    @Test
    fun testAndroidClangCompilerUsesTargetArgInternally() {
        for (version in 16..20) {
            assertTrue(
                androidClangCompilerUsesTargetArgInternally("armv7a-linux-androideabi$version-clang"),
            )
            assertTrue(
                androidClangCompilerUsesTargetArgInternally("armv7a-linux-androideabi$version-clang++"),
            )
        }
        assertFalse(androidClangCompilerUsesTargetArgInternally("clang-i686-linux-android"))
        assertFalse(androidClangCompilerUsesTargetArgInternally("clang"))
        assertFalse(androidClangCompilerUsesTargetArgInternally("clang++"))
    }

    @Test
    fun testIsLlvmMingwWrapper() {
        assertTrue(isLlvmMingwWrapper("x86_64-w64-mingw32-clang"))
        assertTrue(isLlvmMingwWrapper("x86_64-w64-mingw32-clang++"))
        assertFalse(isLlvmMingwWrapper("x86_64-linux-gnu-clang"))
        assertFalse(isLlvmMingwWrapper("clang"))
    }

    @Test
    fun testAsmFileExt() {
        assertEquals(AsmFileExt.DotAsm, AsmFileExt.fromPath("foo.asm"))
        assertEquals(AsmFileExt.DotAsm, AsmFileExt.fromPath("path/to/bar.ASM"))
        assertEquals(AsmFileExt.DotS, AsmFileExt.fromPath("foo.s"))
        assertEquals(AsmFileExt.DotS, AsmFileExt.fromPath("bar.S"))
        assertNull(AsmFileExt.fromPath("foo.c"))
        assertNull(AsmFileExt.fromPath("foo.cpp"))
    }

    @Test
    fun testMapDarwinTargetFromRustToCompilerArchitecture() {
        fun makeTarget(arch: String, fullArch: String): TargetInfo =
            TargetInfo(
                fullArch = fullArch,
                arch = arch,
                vendor = "apple",
                os = "macos",
                env = "",
                abi = "",
            )

        assertEquals("arm64", mapDarwinTargetFromRustToCompilerArchitecture(makeTarget("aarch64", "aarch64")))
        assertEquals("arm64_32", mapDarwinTargetFromRustToCompilerArchitecture(makeTarget("aarch64", "arm64_32")))
        assertEquals("arm64e", mapDarwinTargetFromRustToCompilerArchitecture(makeTarget("aarch64", "arm64e")))
        assertEquals("armv7k", mapDarwinTargetFromRustToCompilerArchitecture(makeTarget("arm", "armv7k")))
        assertEquals("armv7s", mapDarwinTargetFromRustToCompilerArchitecture(makeTarget("arm", "armv7s")))
        assertEquals("i386", mapDarwinTargetFromRustToCompilerArchitecture(makeTarget("x86", "i386")))
        assertEquals("i386", mapDarwinTargetFromRustToCompilerArchitecture(makeTarget("x86", "i686")))
        assertEquals("ppc", mapDarwinTargetFromRustToCompilerArchitecture(makeTarget("powerpc", "powerpc")))
        assertEquals("ppc64", mapDarwinTargetFromRustToCompilerArchitecture(makeTarget("powerpc64", "powerpc64")))
        assertEquals("x86_64", mapDarwinTargetFromRustToCompilerArchitecture(makeTarget("x86_64", "x86_64")))
        assertEquals("x86_64h", mapDarwinTargetFromRustToCompilerArchitecture(makeTarget("x86_64", "x86_64h")))
    }

    @Test
    fun testIsArm() {
        fun makeTarget(arch: String): TargetInfo =
            TargetInfo(
                fullArch = arch,
                arch = arch,
                vendor = "unknown",
                os = "linux",
                env = "gnu",
                abi = "",
            )

        assertTrue(isArm(makeTarget("aarch64")))
        assertTrue(isArm(makeTarget("arm64ec")))
        assertTrue(isArm(makeTarget("arm")))
        assertFalse(isArm(makeTarget("x86_64")))
        assertFalse(isArm(makeTarget("riscv64gc")))
    }

    @Test
    fun testCheckDisabledDefault() {
        assertFalse(isDisabled())
        assertTrue(checkDisabled().isSuccess)
    }

    @Test
    fun testAutodetectAndroidCompiler() {
        val detected = autodetectAndroidCompiler("aarch64-linux-android", "gcc", "clang")
        assertNotNull(detected)
    }
}
