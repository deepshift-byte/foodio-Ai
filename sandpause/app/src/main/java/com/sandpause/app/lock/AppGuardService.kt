package com.sandpause.app.lock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.sandpause.app.data.repo.EventLogger
import com.sandpause.app.data.repo.EventType
import com.sandpause.app.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fallback-mode guard. It only learns which package is in the foreground
 * (window state changes). The service does not read screen content
 * (`canRetrieveWindowContent` is false), and nothing leaves the device.
 */
@AndroidEntryPoint
class AppGuardService : AccessibilityService() {

    @Inject lateinit var guard: ForegroundGuard
    @Inject lateinit var engine: LockEngine
    @Inject lateinit var events: EventLogger
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.launch {
            events.log(EventType.ACCESSIBILITY_ON)
            engine.restore()
            // If a lock is running when the service comes back, the lock screen returns at once.
            if (engine.isLocked) LockLauncher.showLock(applicationContext)
            LockService.start(applicationContext)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        guard.onForeground(pkg) { performGlobalAction(GLOBAL_ACTION_HOME) }
    }

    override fun onInterrupt() {
        // Nothing to interrupt: the service has no feedback of its own.
    }

    /** Disabling the service is logged. The lock screen returns when it is enabled again. */
    override fun onUnbind(intent: Intent?): Boolean {
        scope.launch { events.log(EventType.ACCESSIBILITY_OFF) }
        return super.onUnbind(intent)
    }
}
