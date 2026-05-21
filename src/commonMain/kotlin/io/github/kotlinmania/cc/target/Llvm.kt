// port-lint: source src/target/llvm.rs
package io.github.kotlinmania.cc.target

/**
 * The LLVM/Clang target triple.
 *
 * See <https://clang.llvm.org/docs/CrossCompilation.html#target-triple>.
 *
 * Rust and Clang don't really agree on target naming, so we first try to
 * find the matching triple based on `rustc`'s output, but if no such
 * triple exists, we attempt to construct the triple from scratch.
 *
 * NOTE: You should never need to match on this explicitly, use the
 * fields on [TargetInfo] instead.
 */
internal fun TargetInfo.llvmTarget(rustcTarget: String, version: String?): String {
    if (rustcTarget == "armv7-apple-ios") {
        // FIXME(madsmtm): Unnecessary once we bump MSRV to Rust 1.74
        return "armv7-apple-ios"
    } else if (os == "uefi") {
        // Override the UEFI LLVM targets.
        //
        // The rustc mappings (as of 1.82) for the UEFI targets are:
        // * i686-unknown-uefi -> i686-unknown-windows-gnu
        // * x86_64-unknown-uefi -> x86_64-unknown-windows
        // * aarch64-unknown-uefi -> aarch64-unknown-windows
        //
        // However, in cc-rs all the UEFI targets use
        // -windows-gnu. This has been the case since 2021 [1].
        // * i686-unknown-uefi -> i686-unknown-windows-gnu
        // * x86_64-unknown-uefi -> x86_64-unknown-windows-gnu
        // * aarch64-unknown-uefi -> aarch64-unknown-windows-gnu
        //
        // For now, override the UEFI mapping to keep the behavior
        // of cc-rs unchanged.
        //
        // TODO: as discussed in [2], it may be possible to switch
        // to new UEFI targets added to clang, and regardless it
        // would be good to have consistency between rustc and
        // cc-rs.
        //
        // [1]: https://github.com/rust-lang/cc-rs/pull/623
        // [2]: https://github.com/rust-lang/cc-rs/pull/1264
        return "$fullArch-unknown-windows-gnu"
    }

    // If no version is requested, let's take the triple directly from
    // `rustc` (the logic below is not yet good enough for most targets).
    //
    // FIXME(madsmtm): This should ideally be removed.
    if (version == null) {
        val index = LLVM_TARGETS.binarySearch { it.first.compareTo(rustcTarget) }
        if (index >= 0) {
            val (_, llvmTarget) = LLVM_TARGETS[index]
            return llvmTarget
        }
    }

    // Otherwise, attempt to construct the triple from the target info.

    val arch = when {
        fullArch.startsWith("riscv32") -> "riscv32"
        fullArch.startsWith("riscv64") -> "riscv64"
        fullArch == "aarch64" && vendor == "apple" -> "arm64"
        fullArch == "armv7" && vendor == "sony" -> "thumbv7a" // FIXME
        else -> fullArch
    }
    val mappedVendor = when {
        vendor == "kmc" || vendor == "nintendo" -> "unknown"
        vendor == "unknown" && os == "android" -> "linux"
        vendor == "uwp" -> "pc"
        vendor == "espressif" -> ""
        this.arch == "msp430" -> ""
        else -> vendor
    }
    val mappedOs = when (os) {
        "macos" -> "macosx"
        "visionos" -> "xros"
        "uefi" -> "windows"
        "solid_asp3", "horizon", "teeos", "nuttx", "espidf" -> "none"
        "nto" -> "unknown"    // FIXME
        "trusty" -> "unknown" // FIXME
        else -> os
    }
    val mappedVersion = version ?: ""
    val mappedEnv = when (env) {
        "newlib", "nto70", "nto71", "nto71_iosock", "p1", "p2", "relibc", "sgx", "uclibc" -> ""
        "sim" -> "simulator"
        else -> env
    }
    val mappedAbi = when (abi) {
        "llvm", "softfloat", "uwp", "vec-extabi" -> ""
        "ilp32" -> "_ilp32"
        "abi64" -> ""
        "elfv1", "elfv2" -> ""
        else -> abi
    }
    return when {
        mappedVendor == "" && mappedEnv == "" && mappedAbi == "" ->
            "$arch-$mappedOs$mappedVersion"
        mappedVendor == "" ->
            "$arch-$mappedOs$mappedVersion-$mappedEnv$mappedAbi"
        mappedEnv == "" && mappedAbi == "" ->
            "$arch-$mappedVendor-$mappedOs$mappedVersion"
        else ->
            "$arch-$mappedVendor-$mappedOs$mappedVersion-$mappedEnv$mappedAbi"
    }
}
