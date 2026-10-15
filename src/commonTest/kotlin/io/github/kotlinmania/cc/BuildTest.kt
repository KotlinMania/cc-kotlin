// port-lint: tests src/lib.rs
package io.github.kotlinmania.cc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuildTest {
    @Test
    fun testBuildConfigurationDefaults() {
        val b = Build.new()
        assertEquals(emptyList(), b.getFiles())
        assertFalse(b.cpp)
        assertFalse(b.cuda)
        assertEquals(0, b.cudaFileCount())
        assertFalse(b.getDebug())
        assertEquals(true, b.inheritRustflags)
    }

    @Test
    fun testBuildChaining() {
        val b =
            Build
                .new()
                .file("src/foo.c")
                .file("src/bar.c")
                .include("include")
                .define("FOO", "BAR")
                .define("BAZ")
                .flag("-Wall")
                .flag("-Wextra")
                .cpp(true)
                .target("x86_64-unknown-linux-gnu")
                .optLevel(2u)
                .debug(true)

        assertEquals(listOf("src/foo.c", "src/bar.c"), b.getFiles())
        assertEquals(listOf("include"), b.includeDirectories)
        assertEquals(listOf("FOO" to "BAR", "BAZ" to null), b.definitions)
        assertEquals(listOf("-Wall", "-Wextra"), b.flags)
        assertTrue(b.cpp)
        assertEquals("2", b.getOptLevel().getOrNull())
        assertTrue(b.getDebug())
        assertEquals("x86_64-unknown-linux-gnu", b.getRawTarget().getOrNull())

        b.removeFlag("-Wall")
        assertEquals(listOf("-Wextra"), b.flags)
    }

    @Test
    fun testTargetEnvs() {
        val b = Build.new().target("aarch64-unknown-linux-gnu")
        val envs = b.targetEnvs("CFLAGS").getOrThrow()
        assertEquals(
            listOf(
                "CFLAGS_aarch64-unknown-linux-gnu",
                "CFLAGS_aarch64_unknown_linux_gnu",
                "HOST_CFLAGS",
                "CFLAGS",
            ),
            envs,
        )
    }

    @Test
    fun testPrefixForTarget() {
        val b = Build.new()
        assertEquals("aarch64-linux-gnu", b.prefixForTarget("aarch64-unknown-linux-gnu"))
        assertEquals("x86_64-w64-mingw32", b.prefixForTarget("x86_64-pc-windows-gnu"))
        assertEquals("arm-linux-gnueabihf", b.prefixForTarget("armv7-unknown-linux-gnueabihf"))
        assertEquals("loongarch64-linux-gnu", b.prefixForTarget("loongarch64-unknown-linux-gnu"))
    }
}
