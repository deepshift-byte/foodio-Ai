package com.sandpause.app.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import com.sandpause.core.policy.KnownPackages
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device-owner (primary) enforcement. When SandPause is device owner it can:
 * - pin the lock screen with Lock Task Mode, allowing only listed packages;
 * - block uninstalling itself, safe mode and factory reset from Settings;
 * - hide data-heavy apps while the data budget is spent.
 *
 * Setup is manual: `adb shell dpm set-device-owner com.sandpause.app/.admin.SandPauseAdminReceiver`.
 * Every call here is a no-op unless the app is device owner.
 */
@Singleton
class DeviceOwnerController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dpm: DevicePolicyManager? =
        context.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(context, SandPauseAdminReceiver::class.java)
    private val ownPackage = context.packageName

    fun isDeviceOwner(): Boolean = dpm?.isDeviceOwnerApp(ownPackage) == true

    fun isAdminActive(): Boolean = dpm?.isAdminActive(admin) == true

    /** Locks the app's own settings in place. Safe to call repeatedly. */
    fun applyHardening() {
        val dpm = dpm ?: return
        if (!isDeviceOwner()) return
        runCatching { dpm.setUninstallBlocked(admin, ownPackage, true) }
        runCatching { dpm.setSafeBootDisabled(admin, true) }
        runCatching { dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET) }
    }

    /**
     * Sets the packages allowed in Lock Task Mode. SandPause's own package and the
     * telephony in-call UI are always included, so calls can be placed and ended.
     */
    fun setLockAllowlist(allowedPackages: Set<String>) {
        val dpm = dpm ?: return
        if (!isDeviceOwner()) return
        val packages = (setOf(ownPackage) + KnownPackages.TELECOM_IN_CALL_UI + allowedPackages)
            .toTypedArray()
        runCatching { dpm.setLockTaskPackages(admin, packages) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // No Home, Recents, notifications or keyguard features: this blocks the status bar too.
            runCatching { dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE) }
        }
    }

    /** Hides (or unhides) apps. Used for data-heavy apps once the budget is spent. */
    fun setAppsHidden(packages: Set<String>, hidden: Boolean) {
        val dpm = dpm ?: return
        if (!isDeviceOwner()) return
        packages.forEach { pkg ->
            runCatching { dpm.setApplicationHidden(admin, pkg, hidden) }
        }
    }
}
