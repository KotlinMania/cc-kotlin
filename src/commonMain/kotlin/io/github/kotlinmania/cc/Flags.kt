// port-lint: source src/flags.rs
package io.github.kotlinmania.cc

import io.github.kotlinmania.cc.target.TargetInfo

/**
 * Codegen flags parsed from `CARGO_ENCODED_RUSTFLAGS`.
 */
data class RustcCodegenFlags(
    var branchProtection: String? = null,
    var codeModel: String? = null,
    var noVectorizeLoops: Boolean = false,
    var noVectorizeSlp: Boolean = false,
    var profileGenerate: String? = null,
    var profileUse: String? = null,
    var controlFlowGuard: String? = null,
    var lto: String? = null,
    var relocationModel: String? = null,
    var embedBitcode: Boolean? = null,
    var forceFramePointers: Boolean? = null,
    var noRedzone: Boolean? = null,
    var softFloat: Boolean? = null,
    var dwarfVersion: UInt? = null,
    var stackProtector: String? = null,
    var linkerPluginLto: Boolean? = null,
) {
    companion object {
        private val FLAG_PREFIXES =
            setOf(
                "-Z",
                "-C",
                "--codegen",
                "-L",
                "-l",
                "-o",
                "-W",
                "--warn",
                "-A",
                "--allow",
                "-D",
                "--deny",
                "-F",
                "--forbid",
            )

        private fun isFlagPrefix(flag: String): Boolean = flag in FLAG_PREFIXES

        private fun handleFlagPrefix(
            prev: String,
            curr: String,
        ): Pair<String, String> =
            when {
                prev == "--codegen" || prev == "-C" -> "-C" to curr
                curr.startsWith("--codegen=") -> "-C" to curr.substring(10)
                prev == "-Z" -> "-Z" to curr
                prev == "-L" || prev == "-l" || prev == "-o" -> prev to curr
                prev == "-W" || prev == "--warn" -> "-W" to curr
                prev == "-A" || prev == "--allow" -> "-A" to curr
                prev == "-D" || prev == "--deny" -> "-D" to curr
                prev == "-F" || prev == "--forbid" -> "-F" to curr
                else -> "" to curr
            }

        /**
         * Parse flags obtained from `CARGO_ENCODED_RUSTFLAGS`.
         */
        fun parse(rustflagsEnv: String): RustcCodegenFlags {
            val codegenFlags = RustcCodegenFlags()
            var prevPrefix: String? = null
            for (curr in rustflagsEnv.split("\u001f")) {
                val prev = prevPrefix.orEmpty()
                prevPrefix = null
                if (prev.isEmpty() && isFlagPrefix(curr)) {
                    prevPrefix = curr
                    continue
                }

                val (prefix, rustcFlag) = handleFlagPrefix(prev, curr)
                codegenFlags.setRustcFlag(prefix, rustcFlag)
            }
            return codegenFlags
        }
    }

    /**
     * Applies a single rustc flag.
     */
    fun setRustcFlag(
        prefix: String,
        flag: String,
    ) {
        fun argToBool(arg: String): Boolean? =
            when (arg) {
                "y", "yes", "on", "true" -> true
                "n", "no", "off", "false" -> false
                else -> null
            }

        fun argToU32(arg: String): UInt? = arg.toUIntOrNull()

        val splitIndex = flag.indexOf('=')
        val (flagName, value) =
            if (splitIndex != -1) {
                flag.substring(0, splitIndex) to flag.substring(splitIndex + 1)
            } else {
                flag to null
            }

        val fullFlag = if (prefix.isEmpty()) flagName else "$prefix$flagName"

        fun <T> flagNotEmpty(flagValue: T?): T =
            flagValue ?: throw Error.new(
                ErrorKind.InvalidFlag,
                "$fullFlag must have a value",
            )

        when (fullFlag) {
            "-Ccode-model" -> {
                codeModel = flagNotEmpty(value)
            }
            "-Cno-vectorize-loops" -> noVectorizeLoops = true
            "-Cno-vectorize-slp" -> noVectorizeSlp = true
            "-Cprofile-generate" -> {
                profileGenerate = flagNotEmpty(value)
            }
            "-Cprofile-use" -> {
                profileUse = flagNotEmpty(value)
            }
            "-Ccontrol-flow-guard" -> controlFlowGuard = value ?: "true"
            "-Clto" -> lto = value ?: "true"
            "-Clinker-plugin-lto" -> linkerPluginLto = true
            "-Crelocation-model" -> {
                relocationModel = flagNotEmpty(value)
            }
            "-Cembed-bitcode" -> embedBitcode = if (value != null) argToBool(value) else true
            "-Cforce-frame-pointers" -> forceFramePointers = if (value != null) argToBool(value) else true
            "-Cno-redzone" -> noRedzone = if (value != null) argToBool(value) else true
            "-Csoft-float" -> softFloat = if (value != null) argToBool(value) else true
            "-Zbranch-protection", "-Cbranch-protection" -> {
                branchProtection = flagNotEmpty(value)
            }
            "-Cdwarf-version" -> {
                dwarfVersion = flagNotEmpty(value?.let { argToU32(it) })
            }
            "-Zstack-protector", "-Cstack-protector" -> {
                stackProtector = flagNotEmpty(value)
            }
        }
    }

    /**
     * Translates parsed rustc flags into C compiler flags.
     */
    fun ccFlags(
        build: Build,
        tool: Tool,
        target: TargetInfo,
    ) {
        val family = tool.family
        val pushIfSupported: (String) -> Unit = { flag ->
            if (build.isFlagSupportedInner(flag, tool, target).getOrDefault(false)) {
                tool.args.add(flag)
            } else {
                build.cargoOutput.printWarning(
                    "Inherited flag \"$flag\" is not supported by the currently used CC",
                )
            }
        }

        val clangOrGnu = family is ToolFamily.Clang || family is ToolFamily.Gnu

        if (clangOrGnu) {
            branchProtection?.let { value ->
                pushIfSupported("-mbranch-protection=${value.replace(",", "+")}")
            }
            codeModel?.let { value ->
                pushIfSupported("-mcmodel=$value")
            }
            if (noVectorizeLoops) {
                pushIfSupported("-fno-vectorize")
            }
            if (noVectorizeSlp) {
                pushIfSupported("-fno-slp-vectorize")
            }
            relocationModel?.let { value ->
                val ccFlag =
                    when (value) {
                        "pic" -> "-fPIC"
                        "pie" -> "-fPIE"
                        "dynamic-no-pic" -> "-mdynamic-no-pic"
                        else -> null
                    }
                if (ccFlag != null) {
                    pushIfSupported(ccFlag)
                }
            }
            forceFramePointers?.let { value ->
                val ccFlag = if (value) "-fno-omit-frame-pointer" else "-fomit-frame-pointer"
                pushIfSupported(ccFlag)
            }
            noRedzone?.let { value ->
                val ccFlag = if (value) "-mno-red-zone" else "-mred-zone"
                pushIfSupported(ccFlag)
            }
            softFloat?.let { value ->
                val ccFlag = if (value) "-msoft-float" else "-mhard-float"
                pushIfSupported(ccFlag)
            }
            dwarfVersion?.let { value ->
                pushIfSupported("-gdwarf-$value")
            }
            stackProtector?.let { value ->
                val ccFlag =
                    when (value) {
                        "strong" -> "-fstack-protector-strong"
                        "all" -> "-fstack-protector-all"
                        else -> null
                    }
                if (ccFlag != null) {
                    pushIfSupported(ccFlag)
                }
            }
        }

        when (family) {
            is ToolFamily.Clang -> {
                profileGenerate?.let { value ->
                    pushIfSupported("-fprofile-generate=$value")
                }
                profileUse?.let { value ->
                    pushIfSupported("-fprofile-use=$value")
                }
                embedBitcode?.let { value ->
                    val ccVal = if (value) "all" else "off"
                    pushIfSupported("-fembed-bitcode=$ccVal")
                }
                if (linkerPluginLto == true) {
                    pushIfSupported("-flto=thin")
                }
                controlFlowGuard?.let { value ->
                    val ccVal =
                        when (value) {
                            "y", "yes", "on", "true", "checks" -> "cf"
                            "nochecks" -> "cf-nochecks"
                            "n", "no", "off", "false" -> "none"
                            else -> null
                        }
                    if (ccVal != null) {
                        pushIfSupported("-mguard=$ccVal")
                    }
                }
            }
            ToolFamily.Gnu -> {}
            is ToolFamily.Msvc -> {
                controlFlowGuard?.let { value ->
                    val ccVal =
                        when (value) {
                            "y", "yes", "on", "true", "checks" -> "cf"
                            "n", "no", "off", "false" -> "cf-"
                            else -> null
                        }
                    if (ccVal != null) {
                        pushIfSupported("/guard:$ccVal")
                    }
                }
                forceFramePointers?.let { value ->
                    if (!target.arch.contains("64")) {
                        val ccFlag = if (value) "/Oy-" else "/Oy"
                        pushIfSupported(ccFlag)
                    }
                }
            }
        }
    }
}
