@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package io.github.kotlinmania.cc

private fun isNodeAvailable(): Boolean =
    js("typeof process !== 'undefined' && typeof process.env !== 'undefined'")

private fun nodeGetEnv(name: String): String? =
    js("process.env[name] !== undefined ? process.env[name] : null")

internal actual fun getEnv(name: String): String? =
    if (isNodeAvailable()) nodeGetEnv(name) else null
