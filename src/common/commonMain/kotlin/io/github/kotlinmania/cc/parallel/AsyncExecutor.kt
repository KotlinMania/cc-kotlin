// port-lint: source parallel/async_executor.rs
package io.github.kotlinmania.cc.parallel

/**
 * A helper that yields once before completing.
 */
class YieldOnce(
    private var yielded: Boolean = false,
) {
    typealias Output = Unit

    /**
     * Poll whether execution has yielded.
     */
    fun poll(): Boolean {
        if (!yielded) {
            yielded = true
            return false
        }
        return true
    }
}

/**
 * Execute two futures/tasks until both complete.
 */
fun blockOn(
    fut1: () -> Result<Unit>,
    fut2: () -> Result<Unit>,
    hasMadeProgress: () -> Boolean,
): Result<Unit> {
    val r2 = fut2()
    if (r2.isFailure) return r2
    val r1 = fut1()
    if (r1.isFailure) return r1
    hasMadeProgress()
    return Result.success(Unit)
}
