package com.sandpause.app.debug

/**
 * Debug builds only. "Fast time" makes one parent-facing minute last 5 seconds,
 * so locks, milestones and allowances can be tested quickly.
 *
 * The release source set has a no-op version of this file.
 */
object DebugHooks {
    const val AVAILABLE: Boolean = true

    @Volatile
    var fastTime: Boolean = false
        private set

    fun setFastTime(on: Boolean) {
        fastTime = on
    }
}
