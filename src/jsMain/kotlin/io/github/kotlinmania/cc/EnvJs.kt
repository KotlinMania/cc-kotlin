package io.github.kotlinmania.cc

internal actual fun getEnv(name: String): String? =
    js("typeof process !== 'undefined' && process.env ? process.env[name] : null") as? String
