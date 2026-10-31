package com.sandpause.core.schedule

import java.time.Duration
import java.time.LocalDateTime

/** Daily screen-time allowance arithmetic. All values in milliseconds. */
object Allowance {

    /** Total allowed screen time today: base allowance plus bonus minutes earned. */
    fun totalAllowanceMs(allowanceMsPerDay: Long, bonusMsToday: Long): Long =
        allowanceMsPerDay + bonusMsToday.coerceAtLeast(0L)

    /** True when the allowance is switched on and today's usage has reached the total. */
    fun isExhausted(allowanceMsPerDay: Long, bonusMsToday: Long, usedMsToday: Long): Boolean =
        allowanceMsPerDay > 0L &&
            usedMsToday >= totalAllowanceMs(allowanceMsPerDay, bonusMsToday)

    /** Time from [now] until the next local midnight, used as the auto-lock length. */
    fun msUntilMidnight(now: LocalDateTime): Long =
        Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis()
}
