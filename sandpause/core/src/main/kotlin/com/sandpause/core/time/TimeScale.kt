package com.sandpause.core.time

/**
 * Converts parent-facing "minutes" into milliseconds.
 *
 * Normal mode: 1 minute = 60 000 ms.
 * Debug "fast time" (debug builds only): 1 minute = 5 000 ms, so a 30-minute
 * lock takes 2.5 real minutes to test.
 */
object TimeScale {
    const val NORMAL_MS_PER_MINUTE = 60_000L
    const val FAST_MS_PER_MINUTE = 5_000L

    fun msPerMinute(fastTime: Boolean): Long =
        if (fastTime) FAST_MS_PER_MINUTE else NORMAL_MS_PER_MINUTE

    fun minutesToMs(minutes: Int, fastTime: Boolean): Long =
        minutes.coerceAtLeast(0).toLong() * msPerMinute(fastTime)

    fun msToMinutesCeil(ms: Long, fastTime: Boolean): Int {
        val per = msPerMinute(fastTime)
        return ((ms.coerceAtLeast(0L) + per - 1) / per).toInt()
    }
}
