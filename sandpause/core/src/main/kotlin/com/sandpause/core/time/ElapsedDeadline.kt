package com.sandpause.core.time

/**
 * A countdown that is measured on the monotonic, elapsed-realtime clock
 * (SystemClock.elapsedRealtime on Android), never on the wall clock.
 *
 * Changing the system date/time or time zone therefore cannot shorten it.
 *
 * - [remainingMs] is the time left as of [anchorElapsedMs] on boot [bootId].
 * - Within the same boot, the remaining time is `remainingMs - (now - anchor)`,
 *   so process death or a service restart loses no time.
 * - If the device rebooted (boot id changed, or elapsed time went backwards),
 *   the time the phone was off is NOT counted, and the last stored remaining
 *   value is kept. This can only lengthen a lock, never shorten it.
 */
data class ElapsedDeadline(
    val remainingMs: Long,
    val anchorElapsedMs: Long,
    val bootId: String,
) {
    /** Time left at [nowElapsedMs] on boot [nowBootId]. Never negative. */
    fun remainingAt(nowElapsedMs: Long, nowBootId: String): Long {
        val rebooted = (bootId.isNotEmpty() && nowBootId.isNotEmpty() && bootId != nowBootId) ||
            nowElapsedMs < anchorElapsedMs
        if (rebooted) return remainingMs.coerceAtLeast(0L)
        return (remainingMs - (nowElapsedMs - anchorElapsedMs)).coerceAtLeast(0L)
    }

    fun isExpiredAt(nowElapsedMs: Long, nowBootId: String): Boolean =
        remainingAt(nowElapsedMs, nowBootId) <= 0L

    /** Re-anchors the deadline at [nowElapsedMs]. Use before changing it or persisting it. */
    fun rebased(nowElapsedMs: Long, nowBootId: String): ElapsedDeadline =
        ElapsedDeadline(remainingAt(nowElapsedMs, nowBootId), nowElapsedMs, nowBootId)

    /** Adds [extraMs] to the countdown, starting from the current remaining time. */
    fun extendedBy(extraMs: Long, nowElapsedMs: Long, nowBootId: String): ElapsedDeadline {
        val base = rebased(nowElapsedMs, nowBootId)
        return base.copy(remainingMs = base.remainingMs + extraMs.coerceAtLeast(0L))
    }

    companion object {
        fun start(durationMs: Long, nowElapsedMs: Long, nowBootId: String): ElapsedDeadline =
            ElapsedDeadline(durationMs.coerceAtLeast(0L), nowElapsedMs, nowBootId)

        val EXPIRED = ElapsedDeadline(0L, 0L, "")
    }
}
