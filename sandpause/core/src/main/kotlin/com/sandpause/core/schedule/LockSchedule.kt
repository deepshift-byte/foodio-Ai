package com.sandpause.core.schedule

import java.time.DayOfWeek

/**
 * A recurring lock window, e.g. "Homework" Mon–Fri 16:00–17:00 or "Bedtime"
 * every night 20:00–06:30.
 *
 * [startMinute] and [endMinute] are minutes after midnight (0..1440). When
 * [endMinute] <= [startMinute] the window crosses midnight.
 */
data class LockSchedule(
    val id: String,
    val name: String,
    val days: Set<DayOfWeek>,
    val startMinute: Int,
    val endMinute: Int,
    val enabled: Boolean = true,
) {
    val crossesMidnight: Boolean get() = endMinute <= startMinute

    val isValid: Boolean
        get() = days.isNotEmpty() &&
            startMinute in 0..1439 &&
            endMinute in 1..1440 &&
            endMinute != startMinute
}
