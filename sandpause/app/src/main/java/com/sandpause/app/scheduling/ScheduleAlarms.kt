package com.sandpause.app.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.sandpause.app.admin.Permissions
import com.sandpause.app.data.settings.SettingsStore
import com.sandpause.app.data.settings.toCore
import com.sandpause.app.lock.LockEngine
import com.sandpause.app.lock.LockLauncher
import com.sandpause.app.lock.LockService
import com.sandpause.core.lock.LockReason
import com.sandpause.core.schedule.LockSchedule
import com.sandpause.core.schedule.ScheduleEvaluator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sets one exact alarm for the next wake-up: the next schedule start, or
 * just after midnight (to refresh allowances and data budgets). The alarm only
 * wakes the device. Lock length is always measured on the monotonic clock.
 */
@Singleton
class ScheduleAlarms @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun scheduleNext(schedules: List<LockSchedule>) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val now = LocalDateTime.now()
        val nextStart = ScheduleEvaluator.nextStart(schedules, now)
        val afterMidnight = now.toLocalDate().plusDays(1).atStartOfDay().plusMinutes(1)
        val target = if (nextStart != null && nextStart.isBefore(afterMidnight)) nextStart else afterMidnight
        val triggerAt = target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val pending = pendingIntent()
        alarmManager.cancel(pending)
        if (Permissions.canScheduleExactAlarms(context)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            // Exact alarms were refused by the user: fall back to an inexact one.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ScheduleReceiver::class.java).setAction(ACTION_TICK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_TICK = "com.sandpause.app.action.SCHEDULE_TICK"
        private const val REQUEST_CODE = 4101
    }
}

/**
 * Starts a schedule lock when a window is active and nothing is locked yet.
 * The length is the time until the window ends. It never stacks on a running lock.
 */
@Singleton
class ScheduleCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: SettingsStore,
    private val engine: LockEngine,
    private val alarms: ScheduleAlarms,
) {
    suspend fun evaluate() {
        val schedules = store.currentSettings().schedules.map { it.toCore() }
        val now = LocalDateTime.now()
        val active = ScheduleEvaluator.activeAt(schedules, now)
        if (active != null && !engine.isLocked) {
            val durationMs = Duration.between(now, active.endsAt).toMillis()
            if (durationMs > 0 && engine.startLock(durationMs, LockReason.SCHEDULE) != null) {
                LockService.start(context)
                LockLauncher.showLock(context)
            }
        }
        alarms.scheduleNext(schedules)
    }
}
