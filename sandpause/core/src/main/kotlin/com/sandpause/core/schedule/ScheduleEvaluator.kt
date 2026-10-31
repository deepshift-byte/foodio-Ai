package com.sandpause.core.schedule

import java.time.DayOfWeek
import java.time.LocalDateTime

/** A lock window that is active right now, and when it ends. */
data class ActiveWindow(val schedule: LockSchedule, val endsAt: LocalDateTime)

object ScheduleEvaluator {

    /**
     * The active window at [now] that ends latest, or null.
     * Handles windows that cross midnight: a 20:00–06:30 bedtime window started
     * yesterday is still active at 02:00 today.
     */
    fun activeAt(schedules: List<LockSchedule>, now: LocalDateTime): ActiveWindow? {
        val today = now.toLocalDate()
        val minute = now.hour * 60 + now.minute
        val yesterday = today.minusDays(1).dayOfWeek
        val todayDow = today.dayOfWeek
        var best: ActiveWindow? = null

        for (s in schedules) {
            if (!s.enabled || !s.isValid) continue
            val candidate: LocalDateTime? = if (!s.crossesMidnight) {
                if (todayDow in s.days && minute >= s.startMinute && minute < s.endMinute) {
                    today.atStartOfDay().plusMinutes(s.endMinute.toLong())
                } else null
            } else {
                when {
                    todayDow in s.days && minute >= s.startMinute ->
                        today.plusDays(1).atStartOfDay().plusMinutes(s.endMinute.toLong())
                    yesterday in s.days && minute < s.endMinute ->
                        today.atStartOfDay().plusMinutes(s.endMinute.toLong())
                    else -> null
                }
            }
            if (candidate != null && (best == null || candidate.isAfter(best.endsAt))) {
                best = ActiveWindow(s, candidate)
            }
        }
        return best
    }

    /**
     * The next moment a window starts strictly after [now], or null if none
     * starts in the next [searchDays] days. Used to set exact alarms.
     */
    fun nextStart(
        schedules: List<LockSchedule>,
        now: LocalDateTime,
        searchDays: Int = 8,
    ): LocalDateTime? {
        var best: LocalDateTime? = null
        for (s in schedules) {
            if (!s.enabled || !s.isValid) continue
            for (offset in 0..searchDays) {
                val date = now.toLocalDate().plusDays(offset.toLong())
                if (date.dayOfWeek !in s.days) continue
                val start = date.atStartOfDay().plusMinutes(s.startMinute.toLong())
                if (start.isAfter(now)) {
                    if (best == null || start.isBefore(best)) best = start
                    break
                }
            }
        }
        return best
    }

    /** Helper for readable test data and UI: Mon–Fri. */
    val SCHOOL_DAYS: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )
    val EVERY_DAY: Set<DayOfWeek> = DayOfWeek.entries.toSet()
}
