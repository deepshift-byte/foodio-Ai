package com.sandpause.core.security

import com.sandpause.core.time.ElapsedDeadline

/**
 * Wrong-PIN throttling.
 *
 * After [MAX_FAILURES] wrong attempts the PIN pad is locked for 5 minutes. Each
 * further lockout doubles the cooldown (5, 10, 20, 40, ... minutes, capped at
 * [MAX_COOLDOWN_MINUTES]). A correct PIN resets everything.
 *
 * The cooldown is an [ElapsedDeadline], so changing the clock cannot end it early.
 */
data class AttemptState(
    val failures: Int = 0,
    val lockouts: Int = 0,
    val cooldown: ElapsedDeadline? = null,
)

object AttemptLimiter {
    const val MAX_FAILURES = 5
    const val BASE_COOLDOWN_MINUTES = 5L
    const val MAX_COOLDOWN_MINUTES = 24L * 60L

    /** Cooldown length in minutes for the n-th lockout (n starts at 1). */
    fun cooldownMinutesFor(lockoutNumber: Int): Long {
        if (lockoutNumber < 1) return 0L
        var minutes = BASE_COOLDOWN_MINUTES
        repeat(lockoutNumber - 1) {
            minutes *= 2
            if (minutes >= MAX_COOLDOWN_MINUTES) return MAX_COOLDOWN_MINUTES
        }
        return minutes
    }

    fun remainingCooldownMs(state: AttemptState, nowElapsedMs: Long, nowBootId: String): Long =
        state.cooldown?.remainingAt(nowElapsedMs, nowBootId) ?: 0L

    fun isCoolingDown(state: AttemptState, nowElapsedMs: Long, nowBootId: String): Boolean =
        remainingCooldownMs(state, nowElapsedMs, nowBootId) > 0L

    /** Call after a wrong PIN. Starts a cooldown when the failure limit is reached. */
    fun onWrongAttempt(state: AttemptState, nowElapsedMs: Long, nowBootId: String): AttemptState {
        val failures = state.failures + 1
        if (failures < MAX_FAILURES) return state.copy(failures = failures)
        val lockouts = state.lockouts + 1
        val minutes = cooldownMinutesFor(lockouts)
        return AttemptState(
            failures = 0,
            lockouts = lockouts,
            cooldown = ElapsedDeadline.start(minutes * 60_000L, nowElapsedMs, nowBootId),
        )
    }

    /** Call after a correct PIN. */
    fun onCorrectAttempt(): AttemptState = AttemptState()

    /** Failures left before the next cooldown (never below zero). */
    fun attemptsLeft(state: AttemptState): Int = (MAX_FAILURES - state.failures).coerceAtLeast(0)
}
