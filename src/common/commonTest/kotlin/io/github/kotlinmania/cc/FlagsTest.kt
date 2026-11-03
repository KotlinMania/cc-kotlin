// port-lint: tests src/flags.rs
package io.github.kotlinmania.cc

import kotlin.test.Test
import kotlin.test.assertEquals

class FlagsTest {
    private fun check(
        env: String,
        expected: RustcCodegenFlags,
    ) {
        val actual = RustcCodegenFlags.parse(env)
        assertEquals(expected, actual)
    }

    @Test
    fun codegenType() {
        val expected = RustcCodegenFlags(codeModel = "tiny")
        check("-Ccode-model=tiny", expected)
        check("-C\u001fcode-model=tiny", expected)
        check("--codegen\u001fcode-model=tiny", expected)
        check("--codegen=code-model=tiny", expected)
    }

    @Test
    fun precedence() {
        check(
            "-ccode-model=tiny\u001f-Ccode-model=small",
            RustcCodegenFlags(codeModel = "small"),
        )
    }

    @Test
    fun twoValidPrefixes() {
        val expected = RustcCodegenFlags()
        check("-L\u001f-Clto", expected)
    }

    @Test
    fun stackProtector() {
        val expected = RustcCodegenFlags(stackProtector = "strong")
        check("-Zstack-protector=strong", expected)
        check("-Cstack-protector=strong", expected)
    }

    @Test
    fun threeValidPrefixes() {
        val expected = RustcCodegenFlags(lto = "true")
        check("-L\u001f-L\u001f-Clto", expected)
    }

    @Test
    fun allRustcFlags() {
        val flags =
            listOf(
                "-Ccode-model=tiny",
                "-Ccontrol-flow-guard=yes",
                "-Cembed-bitcode=no",
                "-Cforce-frame-pointers=yes",
                "-Clto=false",
                "-Clink-dead-code=yes",
                "-Cno-redzone=yes",
                "-Cno-vectorize-loops",
                "-Cno-vectorize-slp",
                "-Cprofile-generate=fooprofile",
                "-Cprofile-use=fooprofile",
                "-Crelocation-model=pic",
                "-Csoft-float=yes",
                "-Zbranch-protection=bti,pac-ret,leaf",
                "-Cdwarf-version=5",
                "-Zstack-protector=strong",
                "--cfg",
                "a",
                "--check-cfg 'cfg(verbose)",
                "-L",
                "/usr/lib/foo",
                "-l",
                "static:+whole-archive=mylib",
                "--crate-type=dylib",
                "--crate-name=foo",
                "--edition=2021",
                "--emit=asm",
                "--print=crate-name",
                "-g",
                "-O",
                "-o",
                "foooutput",
                "--out-dir",
                "foooutdir",
                "--target",
                "aarch64-unknown-linux-gnu",
                "-W",
                "missing-docs",
                "-D",
                "unused-variables",
                "--force-warn",
                "dead-code",
                "-A",
                "unused",
                "-F",
                "unused",
                "--cap-lints",
                "warn",
                "--version",
                "--verbose",
                "-v",
                "--extern",
                "foocrate",
                "--sysroot",
                "fooroot",
                "--error-format",
                "human",
                "--color",
                "auto",
                "--diagnostic-width",
                "80",
                "--remap-path-prefix",
                "foo=bar",
                "--json=artifact",
                "-Car",
                "-Ccodegen-units=1",
                "-Ccollapse-macro-debuginfo=yes",
                "-Cdebug-assertions=yes",
                "-Cdebuginfo=1",
                "-Cdefault-linker-libraries=yes",
                "-Cdlltool=foo",
                "-Cextra-filename=foo",
                "-Cforce-unwind-tables=yes",
                "-Cincremental=foodir",
                "-Cinline-threshold=6",
                "-Cinstrument-coverage",
                "-Clink-arg=-foo",
                "-Clink-args=-foo",
                "-Clink-self-contained=yes",
                "-Clinker=lld",
                "-Clinker-flavor=ld.lld",
                "-Clinker-plugin-lto=/path",
                "-Cllvm-args=foo",
                "-Cmetadata=foo",
                "-Cno-prepopulate-passes",
                "-Cno-stack-check",
                "-Copt-level=3",
                "-Coverflow-checks=yes",
                "-Cpanic=abort",
                "-Cpasses=foopass",
                "-Cprefer-dynamic=yes",
                "-Crelro-level=partial",
                "-Cremark=all",
                "-Crpath=yes",
                "-Csave-temps=yes",
                "-Csplit-debuginfo=packed",
                "-Cstrip=symbols",
                "-Csymbol-mangling-version=v0",
                "-Ctarget-cpu=native",
                "-Ctarget-feature=+sve",
                "-Ztune-cpu=machine",
            )
        check(
            flags.joinToString("\u001f"),
            RustcCodegenFlags(
                codeModel = "tiny",
                controlFlowGuard = "yes",
                embedBitcode = false,
                forceFramePointers = true,
                lto = "false",
                noRedzone = true,
                noVectorizeLoops = true,
                noVectorizeSlp = true,
                profileGenerate = "fooprofile",
                profileUse = "fooprofile",
                relocationModel = "pic",
                softFloat = true,
                branchProtection = "bti,pac-ret,leaf",
                dwarfVersion = 5u,
                stackProtector = "strong",
                linkerPluginLto = true,
            ),
        )
    }
}
