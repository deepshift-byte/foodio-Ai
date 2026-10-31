package com.sandpause.core.hourglass

/**
 * Colour mood of the hourglass.
 *
 * RESTING   yellow: locked, sand is falling (default)
 * IMPATIENT red: the child tapped too much; shown for [HourglassMachine.IMPATIENT_MS]
 * FINISHED  green: the last grain has dropped; the device unlocks
 */
enum class Mood { RESTING, IMPATIENT, FINISHED }

/** What a tap on the hourglass should do. */
sealed interface TapOutcome {
    /** Speak the remaining time. */
    data object SpeakRemaining : TapOutcome

    /** Too many taps: shake, turn red and say "Patience!". */
    data object Patience : TapOutcome

    /** Tapped again inside the speech cooldown: nothing is said. */
    data object Silent : TapOutcome

    /** The lock is already finished. Nothing to do. */
    data object Ignored : TapOutcome
}

data class HourglassState(
    val mood: Mood = Mood.RESTING,
    /** Recent tap timestamps, pruned to the last [HourglassMachine.TAP_WINDOW_MS]. */
    val recentTapsMs: List<Long> = emptyList(),
    val impatientUntilMs: Long = 0L,
    val lastSpokenAtMs: Long = NEVER,
) {
    companion object {
        const val NEVER = Long.MIN_VALUE / 4
    }
}

object HourglassMachine {
    const val TAP_THRESHOLD = 5
    const val TAP_WINDOW_MS = 60_000L
    const val IMPATIENT_MS = 5_000L
    const val SPEAK_COOLDOWN_MS = 3_000L

    /**
     * Applies one tap at [nowMs] (monotonic time) while [remainingMs] is left.
     * The state and the outcome are both returned, so the caller can act on the outcome.
     */
    fun onTap(state: HourglassState, nowMs: Long, remainingMs: Long): Pair<HourglassState, TapOutcome> {
        if (state.mood == Mood.FINISHED || remainingMs <= 0L) {
            return state.copy(mood = Mood.FINISHED) to TapOutcome.Ignored
        }
        val taps = (state.recentTapsMs + nowMs).filter { nowMs - it <= TAP_WINDOW_MS }

        if (taps.size >= TAP_THRESHOLD) {
            val next = state.copy(
                mood = Mood.IMPATIENT,
                recentTapsMs = emptyList(),
                impatientUntilMs = nowMs + IMPATIENT_MS,
                lastSpokenAtMs = nowMs,
            )
            return next to TapOutcome.Patience
        }

        val inCooldown = state.lastSpokenAtMs != HourglassState.NEVER &&
            nowMs - state.lastSpokenAtMs < SPEAK_COOLDOWN_MS
        return if (inCooldown) {
            state.copy(recentTapsMs = taps) to TapOutcome.Silent
        } else {
            state.copy(recentTapsMs = taps, lastSpokenAtMs = nowMs) to TapOutcome.SpeakRemaining
        }
    }

    /**
     * Advances time-based moods. Call on every frame or tick with the current
     * remaining time. When the sand runs out, the mood becomes FINISHED.
     */
    fun onTick(state: HourglassState, nowMs: Long, remainingMs: Long): HourglassState {
        if (state.mood == Mood.FINISHED) return state
        if (remainingMs <= 0L) return state.copy(mood = Mood.FINISHED, recentTapsMs = emptyList())
        if (state.mood == Mood.IMPATIENT && nowMs >= state.impatientUntilMs) {
            return state.copy(mood = Mood.RESTING)
        }
        return state
    }
}
