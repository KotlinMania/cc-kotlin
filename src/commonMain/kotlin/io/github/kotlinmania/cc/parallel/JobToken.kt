// port-lint: source parallel/job_token.rs
package io.github.kotlinmania.cc.parallel

import io.github.kotlinmania.cc.getEnv
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Token representing capacity to run a compilation task.
 */
@OptIn(ExperimentalAtomicApi::class)
class JobToken internal constructor(
    private val onDrop: (() -> Unit)? = null,
) : AutoCloseable {
    private var dropped = false

    fun drop() {
        if (!dropped) {
            dropped = true
            onDrop?.invoke()
        }
    }

    override fun close() {
        drop()
    }

    companion object {
        fun new(onDrop: (() -> Unit)? = null): JobToken = JobToken(onDrop)
    }
}

@OptIn(ExperimentalAtomicApi::class)
internal class InProcessJobServer(
    parallelism: Int = defaultParallelism(),
) {
    private val tokens = AtomicInt(parallelism)

    fun acquire(): JobToken {
        while (true) {
            val current = tokens.load()
            if (current > 0) {
                if (tokens.compareAndSet(current, current - 1)) {
                    return JobToken.new { releaseTokenRaw() }
                }
            } else {
                return JobToken.new { releaseTokenRaw() }
            }
        }
    }

    fun releaseTokenRaw() {
        tokens.addAndFetch(1)
    }

    companion object {
        private fun defaultParallelism(): Int {
            val envJobs = getEnv("NUM_JOBS")?.toIntOrNull()
            return envJobs ?: 4
        }

        fun new(): InProcessJobServer = InProcessJobServer()
    }
}

internal class InheritedJobServer private constructor() {
    fun enterActive(): InheritedActiveJobServer = InheritedActiveJobServer()

    fun releaseTokenRaw() {}

    companion object {
        fun fromEnv(): InheritedJobServer? {
            val flags = getEnv("CARGO_MAKEFLAGS") ?: getEnv("MAKEFLAGS") ?: getEnv("MFLAGS")
            return if (flags != null && flags.contains("--jobserver-auth=")) {
                InheritedJobServer()
            } else {
                null
            }
        }

        fun getGlobalImplicitToken(): JobToken? = JobToken.new()
    }
}

internal class InheritedActiveJobServer {
    fun acquire(): JobToken = JobToken.new()

    fun releaseTokenRaw() {}

    companion object {
        fun new(): InheritedActiveJobServer = InheritedActiveJobServer()
    }
}

internal sealed interface JobTokenServer {
    data class Inherited(
        val server: InheritedJobServer,
    ) : JobTokenServer

    data class InProcess(
        val server: InProcessJobServer,
    ) : JobTokenServer

    companion object {
        private val globalServer: JobTokenServer by lazy {
            InheritedJobServer.fromEnv()?.let { Inherited(it) } ?: InProcess(InProcessJobServer.new())
        }

        fun new(): JobTokenServer = globalServer
    }
}

internal class ActiveJobTokenServer internal constructor(
    private val server: JobTokenServer,
) {
    fun acquire(): JobToken =
        when (server) {
            is JobTokenServer.Inherited -> server.server.enterActive().acquire()
            is JobTokenServer.InProcess -> server.server.acquire()
        }

    fun releaseTokenRaw() {
        when (server) {
            is JobTokenServer.Inherited -> server.server.releaseTokenRaw()
            is JobTokenServer.InProcess -> server.server.releaseTokenRaw()
        }
    }

    companion object {
        fun new(): ActiveJobTokenServer = ActiveJobTokenServer(JobTokenServer.new())
    }
}
