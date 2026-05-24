// port-lint: source utilities.rs
package io.github.kotlinmania.cc

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference

internal class JoinOsStrs(
    val slice: List<String>,
    val delimiter: Char,
) {
    override fun toString(): String = buildString {
        val len = slice.size
        for ((index, osStr) in slice.withIndex()) {
            // Upstream note: use native OS-string display once it is stabilized;
            // path and OS-string display use the same formatting.
            append(osStr)
            if (index + 1 < len) {
                append(delimiter)
            }
        }
    }
}

internal class OptionOsStrDisplay(val value: String?) {
    override fun toString(): String =
        // Upstream note: use native OS-string display once it is stabilized;
        // path and OS-string display use the same formatting.
        if (value != null) "Some($value)" else "None"
}

internal class OnceLock<T : Any> {
    private val state = AtomicInt(STATE_UNINIT)
    private val slot = AtomicReference<T?>(null)

    private fun isInitialized(): Boolean = state.load() == STATE_DONE

    private fun getUnchecked(): T = slot.load()!!

    fun getOrInit(f: () -> T): T {
        if (isInitialized()) {
            return getUnchecked()
        }
        while (true) {
            if (state.compareAndSet(STATE_UNINIT, STATE_RUNNING)) {
                val value = f()
                slot.store(value)
                state.store(STATE_DONE)
                return value
            }
            when (state.load()) {
                STATE_DONE -> return getUnchecked()
                else -> { /* another initializer is running; spin until done */ }
            }
        }
    }

    fun get(): T? =
        if (isInitialized()) {
            // Safe because checked isInitialized
            slot.load()
        } else {
            null
        }

    override fun toString(): String {
        val v = get()
        return if (v != null) "OnceLock($v)" else "OnceLock(<uninit>)"
    }

    companion object {
        fun <T : Any> new(): OnceLock<T> = OnceLock()

        fun <T : Any> default(): OnceLock<T> = new()

        private const val STATE_UNINIT = 0
        private const val STATE_RUNNING = 1
        private const val STATE_DONE = 2
    }
}
