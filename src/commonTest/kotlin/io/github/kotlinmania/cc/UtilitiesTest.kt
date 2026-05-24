// port-lint: source utilities.rs
package io.github.kotlinmania.cc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UtilitiesTest {
    @Test
    fun joinOsStrsFormatsWithDelimiter() {
        assertEquals(
            "include:src:generated",
            JoinOsStrs(listOf("include", "src", "generated"), ':').toString(),
        )
    }

    @Test
    fun optionOsStrDisplayMatchesUpstreamFormatting() {
        assertEquals("Some(out/lib)", OptionOsStrDisplay("out/lib").toString())
        assertEquals("None", OptionOsStrDisplay(null).toString())
    }

    @Test
    fun onceLockInitializesOnce() {
        val lock = OnceLock.new<String>()
        var calls = 0

        assertNull(lock.get())
        assertEquals("configured", lock.getOrInit {
            calls += 1
            "configured"
        })
        assertEquals("configured", lock.getOrInit {
            calls += 1
            "ignored"
        })
        assertEquals("configured", lock.get())
        assertEquals(1, calls)
    }

    @Test
    fun onceLockDefaultStartsUninitialized() {
        val lock = OnceLock.default<String>()

        assertNull(lock.get())
        assertEquals("OnceLock(<uninit>)", lock.toString())
    }
}
