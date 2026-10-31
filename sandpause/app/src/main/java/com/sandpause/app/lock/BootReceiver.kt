package com.sandpause.app.lock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.sandpause.app.data.repo.EventLogger
import com.sandpause.app.data.repo.EventType
import com.sandpause.app.data.settings.SettingsStore
import com.sandpause.app.di.ApplicationScope
import com.sandpause.app.scheduling.ScheduleCoordinator
import com.sandpause.app.scheduling.WatchdogWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Restores protection after a reboot (BOOT_COMPLETED and LOCKED_BOOT_COMPLETED,
 * so it also works before the first unlock) and after the service is restarted.
 * Settings and the running lock live in device-protected storage.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var store: SettingsStore
    @Inject lateinit var events: EventLogger
    @Inject lateinit var schedules: ScheduleCoordinator
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pending = goAsync()
        scope.launch {
            try {
                if (!store.currentSettings().setupDone) return@launch
                when (action) {
                    ACTION_RESTART -> events.log(EventType.SERVICE_RESTARTED)
                    Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
                    else -> events.log(EventType.BOOT_RESTORED, action)
                }
                LockService.start(context)
                WatchdogWorker.schedule(context)
                schedules.evaluate()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_RESTART = "com.sandpause.app.action.RESTART_SERVICE"
    }
}

/** Restarts the lock service after the app is swiped from recents or the process is killed. */
object ServiceRestarter {
    private const val REQUEST_CODE = 4202

    fun scheduleRestart(context: Context) {
        val intent = Intent(context, BootReceiver::class.java).setAction(BootReceiver.ACTION_RESTART)
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        alarm.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 1_000L, pending)
    }
}
