package io.github.kotlinmania.cc

internal actual fun getEnv(name: String): String? = System.getenv(name)
