package com.sandpause.app.data.repo

import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.TrafficStats
import com.sandpause.app.data.db.DailyUsageDao
import com.sandpause.app.data.db.DailyUsageEntity
import com.sandpause.app.data.settings.DataStateDto
import com.sandpause.app.data.settings.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Daily screen time (unlocked, screen on), bonus minutes and mobile data.
 *
 * Data usage tries NetworkStatsManager first. Some devices refuse mobile totals
 * to apps without privileged phone-state access. In that case it falls back to
 * TrafficStats (device-wide mobile bytes), measured from the first reading of
 * the day.
 */
@Singleton
class UsageTracker @Inject constructor(
    private val usage: DailyUsageDao,
    private val store: SettingsStore,
    @ApplicationContext private val context: Context,
) {
    fun todayKey(): String = LocalDate.now().toString()

    private suspend fun today(): DailyUsageEntity =
        usage.get(todayKey()) ?: DailyUsageEntity(day = todayKey())

    suspend fun addScreenMs(ms: Long) {
        if (ms <= 0L) return
        val cur = today()
        usage.upsert(cur.copy(screenMs = cur.screenMs + ms))
    }

    suspend fun screenMsToday(): Long = usage.get(todayKey())?.screenMs ?: 0L

    suspend fun bonusMsToday(): Long = usage.get(todayKey())?.bonusMs ?: 0L

    suspend fun addBonusMs(ms: Long) {
        val cur = today()
        usage.upsert(cur.copy(bonusMs = cur.bonusMs + ms))
    }

    fun since(from: LocalDate): Flow<List<DailyUsageEntity>> = usage.since(from.toString())

    /** Mobile bytes used since local midnight, and stored for reports. */
    suspend fun dataBytesToday(): Long {
        val bytes = networkStatsToday() ?: trafficStatsToday()
        val cur = today()
        usage.upsert(cur.copy(dataBytes = bytes))
        return bytes
    }

    private fun networkStatsToday(): Long? {
        return try {
            val nsm = context.getSystemService(NetworkStatsManager::class.java) ?: return null
            val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val bucket = nsm.querySummaryForDevice(
                ConnectivityManager.TYPE_MOBILE,
                null,
                start,
                System.currentTimeMillis(),
            )
            bucket.rxBytes + bucket.txBytes
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun trafficStatsToday(): Long {
        val current = TrafficStats.getMobileRxBytes() + TrafficStats.getMobileTxBytes()
        if (current < 0L) return 0L // counters not supported on this device
        val day = todayKey()
        val state = store.currentData()
        val needsNewBaseline = state.day != day || state.baselineBytes < 0L || current < state.baselineBytes
        if (needsNewBaseline) {
            store.updateData { DataStateDto(day = day, blocked = false, baselineBytes = current) }
            return 0L
        }
        return current - state.baselineBytes
    }

    companion object {
        const val MB = 1024L * 1024L

        fun budgetExceeded(budgetMb: Int, bytesUsed: Long): Boolean =
            budgetMb > 0 && bytesUsed >= budgetMb * MB
    }
}
