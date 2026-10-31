package com.sandpause.core.hourglass

/**
 * A spoken line, described without text. The app maps each case to a localized
 * string resource, so English and Luganda share one selection rule.
 */
sealed interface VoiceLine {
    /** "3 minutes to go! Go and play!" */
    data class Remaining(val minutes: Int) : VoiceLine

    /** "Almost there — less than a minute!" */
    data object AlmostThere : VoiceLine

    /** "Patience! The sand is still falling." */
    data object Patience : VoiceLine

    /** "Well done! You can use the phone now." */
    data object Done : VoiceLine

    /** Spoken at 5, 2 and 1 minute(s) remaining. */
    data class Milestone(val minutes: Int) : VoiceLine
}

object VoiceLines {
    private const val ONE_MINUTE_MS = 60_000L

    /** The line for a tap, based on the remaining time. */
    fun forRemaining(remainingMs: Long): VoiceLine {
        if (remainingMs < ONE_MINUTE_MS) return VoiceLine.AlmostThere
        val minutes = ((remainingMs + ONE_MINUTE_MS - 1) / ONE_MINUTE_MS).toInt()
        return VoiceLine.Remaining(minutes)
    }

    /** Milestones (5, 2, 1 minutes) crossed between two remaining-time readings. */
    fun milestonesCrossed(previousRemainingMs: Long, nowRemainingMs: Long): List<VoiceLine.Milestone> {
        if (nowRemainingMs >= previousRemainingMs) return emptyList()
        return MILESTONE_MINUTES
            .filter { m ->
                val boundary = m * ONE_MINUTE_MS
                previousRemainingMs > boundary && nowRemainingMs <= boundary
            }
            .map { VoiceLine.Milestone(it.toInt()) }
    }

    val MILESTONE_MINUTES: List<Long> = listOf(5L, 2L, 1L)
}
