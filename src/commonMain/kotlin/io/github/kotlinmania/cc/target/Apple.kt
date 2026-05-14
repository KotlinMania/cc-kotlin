// port-lint: source src/target/apple.rs
package io.github.kotlinmania.cc.target

internal fun TargetInfo.appleSdkName(): String =
    // The `env` value, as written here:
    // https://doc.rust-lang.org/reference/conditional-compilation.html#target_env
    // is only really used for disambiguation, so we use a "default" case instead of
    // checking for a blank string.
    when {
        os == "macos" -> "macosx"
        os == "ios" && env == "sim" -> "iphonesimulator"
        os == "ios" && env == "macabi" -> "macosx"
        os == "ios" -> "iphoneos"
        os == "tvos" && env == "sim" -> "appletvsimulator"
        os == "tvos" -> "appletvos"
        os == "watchos" && env == "sim" -> "watchsimulator"
        os == "watchos" -> "watchos"
        os == "visionos" && env == "sim" -> "xrsimulator"
        os == "visionos" -> "xros"
        else -> error("invalid Apple target OS $os")
    }

internal fun TargetInfo.appleVersionFlag(minVersion: String): String {
    // There are many aliases for these, and `-mtargetos=` is preferred on Clang nowadays, but
    // for compatibility with older Clang, we use the earliest supported name here.
    //
    // NOTE: GCC does not support `-miphoneos-version-min=` etc. (because it does not support
    // iOS in general), but we specify them anyhow in case we actually have a Clang-like
    // compiler disguised as a GNU-like compiler, or in case GCC adds support for these in the
    // future.
    //
    // See also:
    // https://clang.llvm.org/docs/ClangCommandLineReference.html#cmdoption-clang-mmacos-version-min
    // https://clang.llvm.org/docs/AttributeReference.html#availability
    // https://gcc.gnu.org/onlinedocs/gcc/Darwin-Options.html#index-mmacosx-version-min
    return when {
        // The `env` value, as written here:
        // https://doc.rust-lang.org/reference/conditional-compilation.html#target_env
        // is only really used for disambiguation, so we use a "default" case instead of
        // checking for a blank string.
        os == "macos" -> "-mmacosx-version-min=$minVersion"
        os == "ios" && env == "sim" -> "-mios-simulator-version-min=$minVersion"
        os == "ios" && env == "macabi" -> "-mtargetos=ios$minVersion-macabi"
        os == "ios" -> "-miphoneos-version-min=$minVersion"
        os == "tvos" && env == "sim" -> "-mappletvsimulator-version-min=$minVersion"
        os == "tvos" -> "-mappletvos-version-min=$minVersion"
        os == "watchos" && env == "sim" -> "-mwatchsimulator-version-min=$minVersion"
        os == "watchos" -> "-mwatchos-version-min=$minVersion"
        // `-mxros-version-min` does not exist
        // https://github.com/llvm/llvm-project/issues/88271
        os == "visionos" && env == "sim" -> "-mtargetos=xros$minVersion-simulator"
        os == "visionos" -> "-mtargetos=xros$minVersion"
        else -> error("invalid Apple target OS $os")
    }
}
