package com.sandpause.core.lock

import com.sandpause.core.time.ElapsedDeadline

enum class LockReason { MANUAL, SCHEDULE, ALLOWANCE }

/**
 * One running lock. [totalMs] is the full length of the lock including any time
 * added later; the hourglass shows [remainingMs] / [totalMs].
 */
data class LockSession(
    val id: Long,
    val reason: LockReason,
    val startedWallMs: Long,
    val totalMs: Long,
    val deadline: ElapsedDeadline,
) {
    fun remainingMs(nowElapsedMs: Long, nowBootId: String): Long =
        deadline.remainingAt(nowElapsedMs, nowBootId)

    fun isFinished(nowElapsedMs: Long, nowBootId: String): Boolean =
        remainingMs(nowElapsedMs, nowBootId) <= 0L

    /** Fraction of the lock still to run, 0f..1f. Drives the sand levels. */
    fun fraction(nowElapsedMs: Long, nowBootId: String): Float {
        if (totalMs <= 0L) return 0f
        val r = remainingMs(nowElapsedMs, nowBootId).toFloat() / totalMs.toFloat()
        return r.coerceIn(0f, 1f)
    }

    /** Adds [extraMs] of lock time. The total grows too, so the sand refills proportionally. */
    fun addTime(extraMs: Long, nowElapsedMs: Long, nowBootId: String): LockSession =
        copy(
            totalMs = totalMs + extraMs.coerceAtLeast(0L),
            deadline = deadline.extendedBy(extraMs, nowElapsedMs, nowBootId),
        )

    companion object {
        fun start(
            id: Long,
            reason: LockReason,
            wallMs: Long,
            durationMs: Long,
            nowElapsedMs: Long,
            nowBootId: String,
        ): LockSession = LockSession(
            id = id,
            reason = reason,
            startedWallMs = wallMs,
            totalMs = durationMs.coerceAtLeast(0L),
            deadline = ElapsedDeadline.start(durationMs, nowElapsedMs, nowBootId),
        )
    }
}
