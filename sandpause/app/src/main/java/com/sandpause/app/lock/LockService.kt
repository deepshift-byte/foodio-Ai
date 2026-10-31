package com.sandpause.app.lock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.sandpause.app.R
import com.sandpause.app.admin.DeviceOwnerController
import com.sandpause.app.admin.Permissions
import com.sandpause.app.data.repo.EventLogger
import com.sandpause.app.data.repo.EventType
import com.sandpause.app.di.ApplicationScope
import com.sandpause.app.MainActivity
import com.sandpause.app.scheduling.ScheduleCoordinator
import com.sandpause.app.data.settings.SettingsStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Long-running foreground service. It keeps the lock engine ticking, enforces
 * the data budget, runs the usage-stats backup detector, and watches screen and
 * clock changes. It restarts itself if it is killed.
 */
@AndroidEntryPoint
class LockService : LifecycleService() {

    @Inject lateinit var engine: LockEngine
    @Inject lateinit var store: SettingsStore
    @Inject lateinit var events: EventLogger
    @Inject lateinit var dataBudget: DataBudgetEnforcer
    @Inject lateinit var guard: ForegroundGuard
    @Inject lateinit var schedules: ScheduleCoordinator
    @Inject lateinit var deviceOwner: DeviceOwnerController
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    private var lastUsagePollMs = 0L
    private var lastDataCheckMs = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> engine.onScreenChanged(true)
                Intent.ACTION_SCREEN_OFF -> engine.onScreenChanged(false)
            }
        }
    }

    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // The lock timer is elapsed-based, so it is unaffected. The change is logged, and schedules are re-checked.
            scope.launch {
                events.log(EventType.CLOCK_CHANGED, intent.action ?: "")
                schedules.evaluate()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        startForegroundCompat(buildNotification(locked = engine.isLocked))

        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        ContextCompat.registerReceiver(
            this,
            clockReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        lifecycleScope.launch {
            engine.restore()
            if (engine.isLocked) LockLauncher.showLock(this@LockService)
            engine.session.collect { session ->
                updateNotification(locked = session != null)
                deviceOwner.setLockAllowlist(store.currentSettings().allowedPackages.toSet())
            }
        }

        lifecycleScope.launch {
            while (isActive) {
                engine.tick()
                val nowMs = SystemClock.elapsedRealtime()
                if (nowMs - lastDataCheckMs > DATA_CHECK_MS) {
                    lastDataCheckMs = nowMs
                    runCatching { dataBudget.refresh() }
                }
                if (engine.isLocked && !Permissions.hasAccessibility(this@LockService)) {
                    pollForegroundFromUsageStats()
                }
                delay(if (engine.isLocked) LOCKED_TICK_MS else IDLE_TICK_MS)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    /** The system may kill the service while the app is removed from recents. Schedule a restart. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        ServiceRestarter.scheduleRestart(this)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching { unregisterReceiver(clockReceiver) }
        super.onDestroy()
    }

    /**
     * Backup detector when accessibility is off but usage access is granted: finds
     * apps that moved to the foreground since the last poll.
     */
    private fun pollForegroundFromUsageStats() {
        if (!Permissions.hasUsageAccess(this)) return
        val usm = getSystemService(UsageStatsManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val from = if (lastUsagePollMs == 0L) now - USAGE_WINDOW_MS else lastUsagePollMs
        lastUsagePollMs = now
        val usageEvents = usm.queryEvents(from, now)
        val event = UsageEvents.Event()
        var latest: String? = null
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) latest = event.packageName
        }
        latest?.let { pkg ->
            guard.onForeground(pkg) {
                startActivity(
                    Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(locked: Boolean) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(locked))
    }

    private fun buildNotification(locked: Boolean): Notification {
        val target = if (locked) {
            Intent(this, ChildLockActivity::class.java)
        } else {
            Intent(this, MainActivity::class.java)
        }
        val content = PendingIntent.getActivity(
            this,
            0,
            target,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(
                getString(if (locked) R.string.notification_locked else R.string.notification_active),
            )
            .setContentIntent(content)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "sandpause_service"
        const val NOTIFICATION_ID = 4201
        private const val LOCKED_TICK_MS = 1_000L
        private const val IDLE_TICK_MS = 10_000L
        private const val DATA_CHECK_MS = 5 * 60_000L
        private const val USAGE_WINDOW_MS = 3_000L

        /** Creates the channel the foreground notification uses. Safe to call repeatedly. */
        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.channel_service_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { setShowBadge(false) },
            )
        }

        /** Starts the service. Background starts can be refused by Android; the caller decides what to do. */
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, LockService::class.java))
            }
        }
    }
}
