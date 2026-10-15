package io.github.kotlinmania.cc.parallel

import io.github.kotlinmania.cc.CargoOutput
import io.github.kotlinmania.cc.Child
import io.github.kotlinmania.cc.Command
import io.github.kotlinmania.cc.StderrForwarder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParallelTest {
    @Test
    fun testYieldOnce() {
        val y = YieldOnce()
        assertFalse(y.poll())
        assertTrue(y.poll())
        assertTrue(y.poll())
    }

    @Test
    fun testBlockOn() {
        var ran1 = false
        var ran2 = false
        val res =
            blockOn(
                fut1 = {
                    ran1 = true
                    Result.success(Unit)
                },
                fut2 = {
                    ran2 = true
                    Result.success(Unit)
                },
                hasMadeProgress = { true },
            )
        assertTrue(res.isSuccess)
        assertTrue(ran1)
        assertTrue(ran2)
    }

    @Test
    fun testJobTokenAndServer() {
        val server = InProcessJobServer(parallelism = 2)
        val token1 = server.acquire()
        val token2 = server.acquire()
        token1.drop()
        token2.close()

        val active = ActiveJobTokenServer.new()
        val token = active.acquire()
        token.drop()
    }

    @Test
    fun testCellUpdate() {
        val res = cellUpdate(5) { it * 2 }
        assertEquals(10, res)
    }

    @Test
    fun testStderrHelpers() {
        assertEquals(42, getFlags(42).getOrThrow())
        assertTrue(setFlags(42, 1).isSuccess)
        assertTrue(setNonBlocking(42).isSuccess)

        val child = Child(stderr = byteArrayOf(1, 2, 3))
        assertEquals(3, bytesAvailable(child).getOrThrow())
    }

    @Test
    fun testTryWaitOnChild() {
        val cmd = Command.new("echo")
        val child = Child()
        val forwarder = StderrForwarder.new(child)
        val waitRes = tryWaitOnChild(cmd, child, forwarder)
        assertTrue(waitRes.isSuccess)
    }

    @Test
    fun testRunCommandsInParallel() {
        val cargoOutput = CargoOutput.new()
        val cmds =
            listOf(
                Result.success(Command.new("echo").arg("hello")),
                Result.success(Command.new("echo").arg("world")),
            )
        val res = runCommandsInParallel(cargoOutput, cmds.iterator())
        assertTrue(res.isSuccess)
    }
}
