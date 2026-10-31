package com.sandpause.core.time

import kotlin.math.abs

/**
 * Detects a change to the wall clock (date/time or time zone) by comparing how
 * far the wall clock moved with how far the monotonic clock moved.
 *
 * Returns true when the two disagree by more than [toleranceMs]. A reboot
 * (elapsed time going backwards) is not treated as a clock change here.
 */
object ClockGuard {
    const val DEFAULT_TOLERANCE_MS = 2_000L

    fun isWallClockJump(
        previousWallMs: Long,
        previousElapsedMs: Long,
        nowWallMs: Long,
        nowElapsedMs: Long,
        toleranceMs: Long = DEFAULT_TOLERANCE_MS,
    ): Boolean {
        if (nowElapsedMs < previousElapsedMs) return false // rebooted: monotonic clock reset
        val wallDelta = nowWallMs - previousWallMs
        val elapsedDelta = nowElapsedMs - previousElapsedMs
        return abs(wallDelta - elapsedDelta) > toleranceMs
    }
}
