package com.sandpause.app.lock

import android.content.Context
import android.graphics.PixelFormat
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.sandpause.app.ui.child.ChildLockContent
import com.sandpause.app.ui.theme.SandPauseTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fallback full-screen overlay (SYSTEM_ALERT_WINDOW). It is shown only when a
 * blocked app is in front and the lock screen cannot be seen. It takes over
 * the screen and hands off to the lock screen activity.
 */
@Singleton
class LockOverlay @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coordinator: ChildLockCoordinator,
) {
    private var composeView: ComposeView? = null
    private var owner: OverlayOwner? = null

    fun show() {
        if (composeView != null) return
        if (!com.sandpause.app.admin.Permissions.canDrawOverlays(context)) return
        val windowManager = context.getSystemService(WindowManager::class.java) ?: return

        val lifecycleOwner = OverlayOwner().also { owner = it }
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setContent {
                SandPauseTheme {
                    OverlayBody(onOpenLockScreen = { hide(); LockLauncher.showLock(context) })
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        lifecycleOwner.start()
        runCatching { windowManager.addView(view, params) }
            .onSuccess { composeView = view }
            .onFailure { lifecycleOwner.stop(); owner = null }
    }

    fun hide() {
        val view = composeView ?: return
        composeView = null
        owner?.stop()
        owner = null
        val windowManager = context.getSystemService(WindowManager::class.java)
        runCatching { windowManager?.removeViewImmediate(view) }
    }

    @Composable
    private fun OverlayBody(onOpenLockScreen: () -> Unit) {
        val ui = coordinator.ui.collectAsState().value
        if (ui != null) {
            ChildLockContent(
                ui = ui,
                fraction = coordinator::fractionNow,
                onHourglassTap = coordinator::onHourglassTap,
                onEmergency = onOpenLockScreen,
                onParentPad = onOpenLockScreen,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            MaterialTheme { }
        }
    }
}

/** Gives the overlay's ComposeView the lifecycle and owners a window-attached view needs. */
private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun start() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun stop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}
