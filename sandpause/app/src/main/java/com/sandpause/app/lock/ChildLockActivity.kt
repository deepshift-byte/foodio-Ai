package com.sandpause.app.lock

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.addCallback
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.sandpause.app.MainActivity
import com.sandpause.app.admin.DeviceOwnerController
import com.sandpause.app.ui.child.ChildLockScreen
import com.sandpause.app.ui.theme.SandPauseTheme
import javax.inject.Inject

/**
 * The full-screen lock. Back is swallowed, the screen stays on over the
 * keyguard, and in device-owner mode the app is pinned with Lock Task Mode.
 * Home and Recents are blocked by the lock-task feature set.
 */
@AndroidEntryPoint
class ChildLockActivity : ComponentActivity() {

    @Inject lateinit var engine: LockEngine
    @Inject lateinit var deviceOwner: DeviceOwnerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!engine.isLocked) {
            // Launched without a running lock (for example at boot before restore). Nothing to show.
            finish()
            return
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        )
        enableEdgeToEdge()
        hideSystemBars()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Back does nothing during a lock.
                }
            },
        )

        setContent {
            SandPauseTheme {
                ChildLockScreen(
                    onParentSettings = { openParentArea() },
                    onLockEnded = { finish() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (engine.isLocked && deviceOwner.isDeviceOwner()) startLockTaskIfNeeded()
    }

    override fun onDestroy() {
        if (isInLockTask()) runCatching { stopLockTask() }
        super.onDestroy()
    }

    private fun startLockTaskIfNeeded() {
        if (!isInLockTask()) runCatching { startLockTask() }
    }

    private fun isInLockTask(): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    private fun openParentArea() {
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
