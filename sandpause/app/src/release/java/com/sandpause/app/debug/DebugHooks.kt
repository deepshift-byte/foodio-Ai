package com.sandpause.app.debug

/** Release builds: fast time is never available. */
object DebugHooks {
    const val AVAILABLE: Boolean = false

    val fastTime: Boolean get() = false

    fun setFastTime(@Suppress("UNUSED_PARAMETER") on: Boolean) = Unit
}
