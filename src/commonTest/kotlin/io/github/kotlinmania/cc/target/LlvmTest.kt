// port-lint: source src/target/llvm.rs
package io.github.kotlinmania.cc.target

import kotlin.test.Test
import kotlin.test.assertEquals

class LlvmTest {
    @Test
    fun testOldIosTarget() {
        assertEquals(
            "armv7-apple-ios",
            TargetInfo(
                fullArch = "armv7",
                arch = "armv7",
                vendor = "apple",
                os = "ios",
                env = "",
                abi = "",
            ).llvmTarget("armv7-apple-ios", null),
        )
    }

    @Test
    fun basicLlvmTripleGuessing() {
        assertEquals(
            "aarch64-unknown-linux",
            TargetInfo(
                fullArch = "aarch64",
                arch = "aarch64",
                vendor = "unknown",
                os = "linux",
                env = "",
                abi = "",
            ).llvmTarget("invalid", null),
        )
        assertEquals(
            "x86_64-unknown-linux-gnu",
            TargetInfo(
                fullArch = "x86_64",
                arch = "x86_64",
                vendor = "unknown",
                os = "linux",
                env = "gnu",
                abi = "",
            ).llvmTarget("invalid", null),
        )
        assertEquals(
            "x86_64-unknown-linux-gnueabi",
            TargetInfo(
                fullArch = "x86_64",
                arch = "x86_64",
                vendor = "unknown",
                os = "linux",
                env = "gnu",
                abi = "eabi",
            ).llvmTarget("invalid", null),
        )
        assertEquals(
            "x86_64-apple-macosx",
            TargetInfo(
                fullArch = "x86_64",
                arch = "x86_64",
                vendor = "apple",
                os = "macos",
                env = "",
                abi = "",
            ).llvmTarget("invalid", null),
        )
    }

    @Test
    fun llvmVersion() {
        assertEquals(
            "arm64-apple-ios14.0-simulator",
            TargetInfo(
                fullArch = "aarch64",
                arch = "aarch64",
                vendor = "apple",
                os = "ios",
                env = "sim",
                abi = "",
            ).llvmTarget("aarch64-apple-ios-sim", "14.0"),
        )
        assertEquals(
            "arm64-apple-xros2.0",
            TargetInfo(
                fullArch = "aarch64",
                arch = "aarch64",
                vendor = "apple",
                os = "visionos",
                env = "",
                abi = "",
            ).llvmTarget("aarch64-apple-visionos", "2.0"),
        )
        assertEquals(
            "arm64-apple-ios13.1-macabi",
            TargetInfo(
                fullArch = "aarch64",
                arch = "aarch64",
                vendor = "apple",
                os = "ios",
                env = "",
                abi = "macabi",
            ).llvmTarget("aarch64-apple-ios-macabi", "13.1"),
        )
    }

    @Test
    fun uefi() {
        assertEquals(
            "i686-unknown-windows-gnu",
            TargetInfo(
                fullArch = "i686",
                arch = "x86",
                vendor = "unknown",
                os = "uefi",
                env = "",
                abi = "",
            ).llvmTarget("i686-unknown-uefi", null),
        )
        assertEquals(
            "x86_64-unknown-windows-gnu",
            TargetInfo(
                fullArch = "x86_64",
                arch = "x86_64",
                vendor = "unknown",
                os = "uefi",
                env = "",
                abi = "",
            ).llvmTarget("x86_64-unknown-uefi", null),
        )
        assertEquals(
            "aarch64-unknown-windows-gnu",
            TargetInfo(
                fullArch = "aarch64",
                arch = "aarch64",
                vendor = "unknown",
                os = "uefi",
                env = "",
                abi = "",
            ).llvmTarget("aarch64-unknown-uefi", null),
        )
    }

    // Upstream test `llvm_for_all_rustc_targets` is marked `#[ignore = "not yet done"]`
    // and shells out to the host `rustc` binary to enumerate every target triple
    // and compare against `TargetInfo::from_rustc_target`. The Kotlin
    // counterpart belongs alongside the port of `src/target/parser.rs`, which
    // is where `TargetInfo.fromRustcTarget` will live; subprocess capability
    // is also not available from KMP commonTest. Translated test is deferred
    // until parser.rs lands.
}
