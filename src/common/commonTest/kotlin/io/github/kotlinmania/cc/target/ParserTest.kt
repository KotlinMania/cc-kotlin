// port-lint: tests src/target/parser.rs
package io.github.kotlinmania.cc.target

import io.github.kotlinmania.cc.ErrorKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParserTest {
    // Test tier 1 targets.
    @Test
    fun tier1() {
        val targets =
            listOf(
                "aarch64-unknown-linux-gnu",
                "aarch64-apple-darwin",
                "i686-pc-windows-gnu",
                "i686-pc-windows-msvc",
                "i686-unknown-linux-gnu",
                "x86_64-apple-darwin",
                "x86_64-pc-windows-gnu",
                "x86_64-pc-windows-msvc",
                "x86_64-unknown-linux-gnu",
            )

        for (target in targets) {
            val result = TargetInfo.fromRustcTarget(target)
            assertTrue(result.isSuccess, "Failed to parse target: $target, error: ${result.exceptionOrNull()}")
        }
    }

    // Various custom target names not (or no longer) known by `rustc`.
    @Test
    fun parseExtra() {
        val targets =
            listOf(
                "aarch64-unknown-none-gnu",
                "aarch64-uwp-windows-gnu",
                "arm-frc-linux-gnueabi",
                "arm-unknown-netbsd-eabi",
                "armv7neon-unknown-linux-gnueabihf",
                "armv7neon-unknown-linux-musleabihf",
                "thumbv7-unknown-linux-gnueabihf",
                "thumbv7-unknown-linux-musleabihf",
                "armv7-apple-ios",
                "wasm32-wasi",
                "x86_64-rumprun-netbsd",
                "x86_64-unknown-linux",
                "x86_64-alpine-linux-musl",
                "x86_64-chimera-linux-musl",
                "x86_64-foxkit-linux-musl",
                "arm-poky-linux-gnueabi",
                "x86_64-unknown-moturus",
                "x86_64-unknown-managarm-mlibc",
            )

        for (target in targets) {
            val result = TargetInfo.fromRustcTarget(target)
            assertTrue(result.isSuccess, "Failed to parse extra target: $target, error: ${result.exceptionOrNull()}")
        }
    }

    @Test
    fun unknownEnvDeterminedAsUnknown() {
        val result = TargetInfo.fromRustcTarget("aarch64-unknown-linux-bogus")
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as? io.github.kotlinmania.cc.Error
        assertEquals(ErrorKind.UnknownTarget, error?.kind)
    }

    @Test
    fun parsesAppleEnvsCorrectly() {
        assertEquals(
            TargetInfo(
                fullArch = "aarch64",
                arch = "aarch64",
                vendor = "apple",
                os = "ios",
                env = "macabi",
                abi = "",
            ),
            TargetInfo.fromRustcTarget("aarch64-apple-ios-macabi").getOrThrow(),
        )
        assertEquals(
            TargetInfo(
                fullArch = "aarch64",
                arch = "aarch64",
                vendor = "apple",
                os = "ios",
                env = "sim",
                abi = "",
            ),
            TargetInfo.fromRustcTarget("aarch64-apple-ios-sim").getOrThrow(),
        )
    }

    @Test
    fun parsesSpecialCases() {
        val x86None = TargetInfo.fromRustcTarget("x86_64-unknown-linux-none").getOrThrow()
        assertEquals("x86_64", x86None.fullArch)
        assertEquals("x86_64", x86None.arch)
        assertEquals("unknown", x86None.vendor)
        assertEquals("linux", x86None.os)
        assertEquals("", x86None.env)
        assertEquals("", x86None.abi)

        val vex = TargetInfo.fromRustcTarget("armv7a-vex-v5").getOrThrow()
        assertEquals("armv7a", vex.fullArch)
        assertEquals("arm", vex.arch)
        assertEquals("vex", vex.vendor)
        assertEquals("vexos", vex.os)
        assertEquals("v5", vex.env)
        assertEquals("eabihf", vex.abi)
    }

    private fun targetFromRustcCfgs(
        target: String,
        cfgs: String,
    ): TargetInfo {
        val dashIndex = target.indexOf('-')
        val fullArch = target.substring(0, dashIndex)

        var arch = "invalid-none-set"
        var vendor = "invalid-none-set"
        var os = "invalid-none-set"
        var env = "invalid-none-set"
        var abi = ""

        for (line in cfgs.lines()) {
            val eqIndex = line.indexOf('=')
            if (eqIndex != -1) {
                val name = line.substring(0, eqIndex).trim()
                var value = line.substring(eqIndex + 1).trim()
                value = value.removePrefix("\"").removeSuffix("\"")

                when (name) {
                    "target_arch" -> arch = value
                    "target_vendor" -> vendor = value
                    "target_os" -> os = value
                    "target_env" -> env = value
                    "target_abi" -> abi = value
                }
            }
        }

        if (abi == "macabi" || abi == "sim") {
            assertEquals(env, abi)
            abi = ""
        }

        return TargetInfo(
            fullArch = fullArch,
            arch = arch,
            vendor = vendor,
            os = os,
            env = env,
            abi = abi,
        )
    }

    @Test
    fun targetFromRustcCfgsVerification() {
        val sampleCfgs =
            """
            target_arch="aarch64"
            target_vendor="apple"
            target_os="ios"
            target_env="sim"
            target_abi="sim"
            """.trimIndent()
        val expected = targetFromRustcCfgs("aarch64-apple-ios-sim", sampleCfgs)
        val actual = TargetInfo.fromRustcTarget("aarch64-apple-ios-sim").getOrThrow()
        assertEquals(expected, actual)
    }
}
