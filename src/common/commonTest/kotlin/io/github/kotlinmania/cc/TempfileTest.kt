package io.github.kotlinmania.cc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TempfileTest {
    @Test
    fun randGeneratesValues() {
        val r1 = rand()
        val r2 = rand()
        assertNotEquals(r1, r2)
    }

    @Test
    fun tmpnameContainsSuffix() {
        val name = tmpname(".tmp.c")
        assertTrue(name.endsWith(".tmp.c"))
    }

    @Test
    fun namedTempfileCreatesAndCleansUp() {
        val base = "."
        val tmpRes = NamedTempfile.new(base, ".test.c")
        assertTrue(tmpRes.isSuccess)
        val tmp = tmpRes.getOrThrow()
        try {
            val path = tmp.path()
            assertTrue(path.endsWith(".test.c"))
            val handle = tmp.takeFile()
            assertNotNull(handle)
            handle.use { h ->
                h.write("int main() { return 0; }\n".encodeToByteArray())
                h.flush()
                h.sync()
            }
            assertEquals(null, tmp.takeFile())
        } finally {
            tmp.drop()
        }
    }
}
