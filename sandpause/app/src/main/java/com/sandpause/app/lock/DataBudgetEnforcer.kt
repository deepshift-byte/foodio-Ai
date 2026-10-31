package com.sandpause.app.lock

import com.sandpause.app.admin.DeviceOwnerController
import com.sandpause.app.data.repo.EventLogger
import com.sandpause.app.data.repo.EventType
import com.sandpause.app.data.repo.UsageTracker
import com.sandpause.app.data.settings.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enforces the daily mobile-data budget. Once the budget is spent, the apps the
 * parent tagged "data-heavy" are blocked until midnight. In device-owner mode
 * they are hidden. In fallback mode the foreground guard blocks them.
 */
@Singleton
class DataBudgetEnforcer @Inject constructor(
    private val store: SettingsStore,
    private val usage: UsageTracker,
    private val deviceOwner: DeviceOwnerController,
    private val events: EventLogger,
) {
    suspend fun refresh() {
        val settings = store.currentSettings()
        val today = usage.todayKey()
        val before = store.currentData()
        val wasBlocked = before.day == today && before.blocked

        if (settings.dataBudgetMb <= 0) {
            deviceOwner.setAppsHidden(settings.dataHeavyPackages.toSet(), hidden = false)
            if (wasBlocked) store.updateData { it.copy(blocked = false) }
            return
        }

        // Reading the bytes also refreshes the TrafficStats baseline for a new day.
        val bytes = usage.dataBytesToday()
        val exceeded = UsageTracker.budgetExceeded(settings.dataBudgetMb, bytes)

        deviceOwner.setAppsHidden(settings.dataHeavyPackages.toSet(), hidden = exceeded)
        when {
            exceeded && !wasBlocked -> {
                store.updateData { it.copy(day = today, blocked = true) }
                events.log(EventType.DATA_BUDGET_EXCEEDED, "${bytes / UsageTracker.MB} MB used")
            }
            !exceeded && wasBlocked -> store.updateData { it.copy(day = today, blocked = false) }
            else -> Unit
        }
    }
}
