package com.sandpause.app.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.sandpause.app.R
import com.sandpause.app.data.repo.EventLogger
import com.sandpause.app.data.repo.EventType
import com.sandpause.app.di.ApplicationScope
import com.sandpause.app.lock.LockEngine
import com.sandpause.app.lock.LockLauncher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Device-admin receiver. Its class name is referenced by the adb setup command
 * (`.admin.SandPauseAdminReceiver`), so keep it in this package.
 */
@AndroidEntryPoint
class SandPauseAdminReceiver : DeviceAdminReceiver() {

    @Inject lateinit var events: EventLogger
    @Inject lateinit var engine: LockEngine
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    /**
     * Called when someone tries to turn off the admin. The system shows our warning.
     * Under a lock the lock screen comes back, and the attempt is logged.
     */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        scope.launch {
            events.log(EventType.ADMIN_DISABLE_ATTEMPT)
            if (engine.isLocked) LockLauncher.showLock(context)
        }
        return context.getString(R.string.admin_disable_warning)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        scope.launch { events.log(EventType.ADMIN_DISABLE_ATTEMPT, "admin disabled") }
    }
}
