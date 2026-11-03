package io.github.kotlinmania.cc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolTest {
    @Test
    fun testToolFamilyFlags() {
        val gnu = ToolFamily.Gnu
        val clang = ToolFamily.Clang(zigCc = false)
        val msvc = ToolFamily.Msvc(clangCl = false)
        val clangCl = ToolFamily.Msvc(clangCl = true)

        assertEquals("-Wall", gnu.warningsFlags())
        assertEquals("-Wall", clang.warningsFlags())
        assertEquals("-W4", msvc.warningsFlags())
        assertEquals("-W4", clangCl.warningsFlags())

        assertEquals("-w", gnu.warningsSuppressionFlags())
        assertEquals("-w", clang.warningsSuppressionFlags())
        assertEquals("-W0", msvc.warningsSuppressionFlags())

        assertEquals("-Wextra", gnu.extraWarningsFlags())
        assertEquals("-Wextra", clang.extraWarningsFlags())
        assertNull(msvc.extraWarningsFlags())

        assertEquals("-Werror", gnu.warningsToErrorsFlag())
        assertEquals("-Werror", clang.warningsToErrorsFlag())
        assertEquals("-WX", msvc.warningsToErrorsFlag())

        assertFalse(gnu.verboseStderr())
        assertTrue(clang.verboseStderr())
        assertFalse(msvc.verboseStderr())
    }

    @Test
    fun testToolFamilyDebugAndFramePointer() {
        val gnu = ToolFamily.Gnu
        val toolGnu = Tool.withFamily("gcc", gnu)
        gnu.addForceFramePointer(toolGnu)
        assertEquals(listOf("-fno-omit-frame-pointer"), toolGnu.args())

        val toolGnuDebug = Tool.withFamily("gcc", gnu)
        gnu.addDebugFlags(toolGnuDebug, "1", 4u)
        assertEquals(listOf("-g1", "-gdwarf-4"), toolGnuDebug.args())

        val clang = ToolFamily.Clang()
        val toolClang = Tool.withFamily("clang", clang)
        clang.addDebugFlags(toolClang, "line-directives-only")
        assertEquals(listOf("-gline-directives-only"), toolClang.args())

        val toolGnuLine = Tool.withFamily("gcc", gnu)
        gnu.addDebugFlags(toolGnuLine, "line-directives-only")
        assertEquals(listOf("-g1"), toolGnuLine.args())

        val msvc = ToolFamily.Msvc()
        val toolMsvc = Tool.withFamily("cl.exe", msvc)
        msvc.addDebugFlags(toolMsvc, "full")
        assertEquals(listOf("-Z7"), toolMsvc.args())
    }

    @Test
    fun testToolFamilyClassification() {
        val gnu = Tool.withFamily("gcc", ToolFamily.Gnu)
        assertTrue(gnu.isLikeGnu())
        assertFalse(gnu.isLikeClang())
        assertFalse(gnu.isLikeMsvc())
        assertFalse(gnu.isLikeClangCl())

        val clang = Tool.withFamily("clang", ToolFamily.Clang())
        assertFalse(clang.isLikeGnu())
        assertTrue(clang.isLikeClang())
        assertFalse(clang.isLikeMsvc())
        assertFalse(clang.isLikeClangCl())

        val msvc = Tool.withFamily("cl.exe", ToolFamily.Msvc(clangCl = false))
        assertFalse(msvc.isLikeGnu())
        assertFalse(msvc.isLikeClang())
        assertTrue(msvc.isLikeMsvc())
        assertFalse(msvc.isLikeClangCl())

        val clangCl = Tool.withFamily("clang-cl.exe", ToolFamily.Msvc(clangCl = true))
        assertFalse(clangCl.isLikeGnu())
        assertFalse(clangCl.isLikeClang())
        assertTrue(clangCl.isLikeMsvc())
        assertTrue(clangCl.isLikeClangCl())
        assertTrue(clangCl.supportsPathDelimiter())

        val clangClCuda =
            Tool(
                path = "nvcc",
                family = ToolFamily.Msvc(clangCl = true),
                cuda = true,
            )
        assertFalse(clangClCuda.supportsPathDelimiter())
    }

    @Test
    fun testDuplicateOptimizationFlags() {
        val tool = Tool.withFamily("gcc", ToolFamily.Gnu)
        tool.args.add("-O2")

        assertTrue(tool.isDuplicateOptArg("-O3"))
        assertFalse(tool.isDuplicateOptArg("-g"))
        assertFalse(tool.isDuplicateOptArg(""))
        assertFalse(tool.isDuplicateOptArg("O2"))

        tool.pushOptUnlessDuplicate("-O3")
        assertEquals(listOf("-O2"), tool.args())

        tool.pushOptUnlessDuplicate("-g")
        assertEquals(listOf("-O2", "-g"), tool.args())
    }

    @Test
    fun testMsvcDuplicateOptimizationFlags() {
        val tool = Tool.withFamily("cl.exe", ToolFamily.Msvc())
        tool.args.add("/O2")

        assertTrue(tool.isDuplicateOptArg("/O1"))
        assertFalse(tool.isDuplicateOptArg("/c"))
        assertFalse(tool.isDuplicateOptArg("-O2"))
    }

    @Test
    fun testCudaFlagPrefix() {
        val tool =
            Tool(
                path = "nvcc",
                family = ToolFamily.Gnu,
                cuda = true,
            )
        tool.pushCcArg("-fPIC")
        assertEquals(listOf("-Xcompiler", "-fPIC"), tool.args())
    }

    @Test
    fun testToCommandAndEnv() {
        val tool = Tool.withFamily("gcc", ToolFamily.Gnu)
        tool.args.addAll(listOf("-Wall", "-O2", "-Werror"))
        tool.removeArg("-O2")
        tool.env.add("FOO" to "BAR")

        val cmd = tool.toCommand()
        assertEquals("gcc", cmd.getProgram())
        assertEquals(listOf("-Wall", "-Werror"), cmd.getArgs())
        assertEquals(listOf("FOO" to "BAR"), cmd.getEnvs())

        assertEquals("", tool.ccEnv())
        tool.ccWrapperPath = "ccache"
        tool.ccWrapperArgs.add("--ccache-skip")
        assertEquals("ccache gcc --ccache-skip", tool.ccEnv())
        assertEquals("-Wall -O2 -Werror", tool.cflagsEnv())
    }

    @Test
    fun testFromFindMsvcTools() {
        val mockTool =
            object : FindMsvcTool {
                override fun path(): String = "C:\\msvc\\bin\\cl.exe"

                override fun isClangCl(): Boolean = false

                override fun env(): List<Pair<String, String>> = listOf("INCLUDE" to "C:\\msvc\\include")
            }

        val tool = Tool.fromFindMsvcTools(mockTool)
        assertEquals("C:\\msvc\\bin\\cl.exe", tool.path())
        assertTrue(tool.isLikeMsvc())
        assertFalse(tool.isLikeClangCl())
        assertEquals(listOf("INCLUDE" to "C:\\msvc\\include"), tool.env())
    }

    @Test
    fun testCompilerFamilyCache() {
        val cache = CompilerFamilyCache()
        val key = listOf("gcc", "-v")
        assertNull(cache.get(key))

        cache.insert(key, ToolFamily.Gnu)
        assertEquals(ToolFamily.Gnu, cache.get(key))
    }

    @Test
    fun testCommandAddOutputFile() {
        val cmdMsvc = Command.new("cl.exe")
        commandAddOutputFile(
            cmdMsvc,
            "out.obj",
            CmdAddOutputFileArgs(msvc = true),
        )
        assertEquals(listOf("-Foout.obj"), cmdMsvc.getArgs())

        val cmdGnu = Command.new("gcc")
        commandAddOutputFile(
            cmdGnu,
            "out.o",
            CmdAddOutputFileArgs(gnu = true),
        )
        assertEquals(listOf("-o", "out.o"), cmdGnu.getArgs())
    }

    @Test
    fun testObjectsFromFiles() {
        val files = listOf("src/foo.c", "src/bar.c")
        val objects = objectsFromFiles(files, "build/out").getOrThrow()
        assertEquals(2, objects.size)
        assertEquals("src/foo.c", objects[0].src)
        assertTrue(objects[0].dst.startsWith("build/out/"))
        assertTrue(objects[0].dst.endsWith("-foo.o"))
        assertEquals("src/bar.c", objects[1].src)
        assertTrue(objects[1].dst.endsWith("-bar.o"))
    }
}
