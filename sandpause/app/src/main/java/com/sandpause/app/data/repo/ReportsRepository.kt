package com.sandpause.app.data.repo

import com.sandpause.app.data.db.DailyUsageDao
import com.sandpause.app.data.db.DailyUsageEntity
import com.sandpause.app.data.db.EventDao
import com.sandpause.app.data.db.EventEntity
import com.sandpause.app.data.db.LockSessionDao
import com.sandpause.app.data.db.LockSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class DayPoint(
    val day: LocalDate,
    val screenMinutes: Int,
    val dataMb: Float,
)

data class ReportData(
    val today: DayPoint,
    /** Seven days, oldest first, ending today. */
    val week: List<DayPoint>,
    val locksStarted: Int,
    val locksCompleted: Int,
    val taps: Int,
    val unlockAttempts: Int,
    val blockedAppAttempts: Int,
) {
    val weekScreenMinutes: Int get() = week.sumOf { it.screenMinutes }
    val weekDataMb: Float get() = week.sumOf { it.dataMb.toDouble() }.toFloat()
}

@Singleton
class ReportsRepository @Inject constructor(
    private val usage: DailyUsageDao,
    private val sessions: LockSessionDao,
    private val events: EventDao,
) {
    /** A live report for the last seven days, updated whenever the data changes. */
    fun report(today: LocalDate = LocalDate.now()): Flow<ReportData> {
        val from = today.minusDays(6)
        val fromMs = from.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return combine(
            usage.since(from.toString()),
            sessions.since(fromMs),
            events.since(fromMs, 5_000),
        ) { usageRows, sessionRows, eventRows ->
            build(today, usageRows, sessionRows, eventRows)
        }
    }

    private fun build(
        today: LocalDate,
        usageRows: List<DailyUsageEntity>,
        sessionRows: List<LockSessionEntity>,
        eventRows: List<EventEntity>,
    ): ReportData {
        val byDay = usageRows.associateBy { it.day }
        val week = (6 downTo 0).map { back ->
            val day = today.minusDays(back.toLong())
            val row = byDay[day.toString()]
            DayPoint(
                day = day,
                screenMinutes = ((row?.screenMs ?: 0L) / 60_000L).toInt(),
                dataMb = ((row?.dataBytes ?: 0L).toFloat() / (1024f * 1024f)),
            )
        }
        val todayPoint = week.last()
        val typeCounts = eventRows.groupingBy { it.type }.eachCount()
        return ReportData(
            today = todayPoint,
            week = week,
            locksStarted = sessionRows.size,
            locksCompleted = sessionRows.count { it.completed },
            taps = typeCounts["TAP"] ?: 0,
            unlockAttempts = typeCounts["WRONG_PIN"] ?: 0,
            blockedAppAttempts = typeCounts["BLOCKED_APP"] ?: 0,
        )
    }
}
