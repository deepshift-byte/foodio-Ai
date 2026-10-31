package com.sandpause.app.ui.parent

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandpause.app.admin.DeviceOwnerController
import com.sandpause.app.data.repo.ReportData
import com.sandpause.app.data.repo.ReportsRepository
import com.sandpause.app.data.settings.AppLanguage
import com.sandpause.app.data.settings.ContactDto
import com.sandpause.app.data.settings.ScheduleDto
import com.sandpause.app.data.settings.SettingsDto
import com.sandpause.app.data.settings.SettingsStore
import com.sandpause.app.debug.DebugHooks
import com.sandpause.app.lock.LockEngine
import com.sandpause.app.lock.LockLauncher
import com.sandpause.app.lock.LockService
import com.sandpause.app.scheduling.ScheduleCoordinator
import com.sandpause.app.scheduling.WatchdogWorker
import com.sandpause.app.security.ParentSession
import com.sandpause.app.security.PinManager
import com.sandpause.core.lock.LockSession
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

data class AppEntry(val packageName: String, val label: String)

/** Where the parent area starts: setup, PIN gate, or home. */
enum class ParentStart { SETUP, GATE, HOME }

@HiltViewModel
class ParentViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: SettingsStore,
    private val engine: LockEngine,
    private val pinManager: PinManager,
    private val parentSession: ParentSession,
    private val reports: ReportsRepository,
    private val deviceOwner: DeviceOwnerController,
    private val scheduleCoordinator: ScheduleCoordinator,
) : ViewModel() {

    val settings: StateFlow<SettingsDto> = store.settings
    val session: StateFlow<LockSession?> = engine.session

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    val report: StateFlow<ReportData?> = reports.report(LocalDate.now())
        .map { it as ReportData? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Resolved once the stored settings have been read, so the wrong start screen never flashes. */
    val start: StateFlow<ParentStart?> = flow {
        val s = store.currentSettings()
        emit(
            when {
                !s.setupDone -> ParentStart.SETUP
                parentSession.isVerified() -> ParentStart.HOME
                else -> ParentStart.GATE
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isDeviceOwner: Boolean get() = deviceOwner.isDeviceOwner()
    val fastTimeAvailable: Boolean get() = DebugHooks.AVAILABLE
    val fastTimeOn: Boolean get() = DebugHooks.fastTime

    // ---- locking ----

    fun startQuickLock(minutes: Int) {
        viewModelScope.launch {
            if (engine.startManual(minutes) != null) {
                LockService.start(context)
                LockLauncher.showLock(context)
            }
        }
    }

    fun startCustomLock(hours: Int, minutes: Int) = startQuickLock(hours * 60 + minutes)

    fun endLockNow() {
        viewModelScope.launch { engine.endLock(early = true) }
    }

    fun grantReward(minutes: Int) {
        viewModelScope.launch { engine.grantReward(minutes) }
    }

    // ---- setup and PIN ----

    suspend fun verifyPin(pin: String): PinManager.VerifyResult = pinManager.verify(pin)

    suspend fun recoverPin(answer: String, newPin: String): PinManager.VerifyResult =
        pinManager.resetWithRecoveryAnswer(answer, newPin)

    fun recoveryQuestion(): String = settings.value.recoveryQuestion

    suspend fun completeSetup(
        childName: String,
        language: AppLanguage,
        pin: String,
        question: String,
        answer: String,
    ) {
        pinManager.savePinAndRecovery(pin, question, answer)
        store.updateSettings {
            it.copy(childName = childName.trim(), languageCode = language.code, setupDone = true)
        }
        LockService.start(context)
        WatchdogWorker.schedule(context)
    }

    // ---- settings edits ----

    fun update(transform: (SettingsDto) -> SettingsDto) {
        viewModelScope.launch { store.updateSettings(transform) }
    }

    fun setDailyAllowance(minutes: Int) = update { it.copy(dailyAllowanceMin = minutes.coerceAtLeast(0)) }

    fun setDataBudget(megabytes: Int) = update { it.copy(dataBudgetMb = megabytes.coerceAtLeast(0)) }

    fun setPlayStorePin(on: Boolean) = update { it.copy(playStorePin = on) }

    fun setSuggestions(lines: List<String>) =
        update { it.copy(suggestions = lines.map { line -> line.trim() }.filter { line -> line.isNotEmpty() }) }

    fun addContact(name: String, number: String): Boolean {
        val current = settings.value.contacts
        if (current.size >= MAX_CONTACTS || name.isBlank() || number.isBlank()) return false
        update { it.copy(contacts = it.contacts + ContactDto(name.trim(), number.trim())) }
        return true
    }

    fun removeContact(index: Int) = update { s ->
        s.copy(contacts = s.contacts.filterIndexed { i, _ -> i != index })
    }

    fun setAppAllowed(packageName: String, allowed: Boolean) = update { s ->
        val set = s.allowedPackages.toMutableSet()
        if (allowed) set.add(packageName) else set.remove(packageName)
        s.copy(allowedPackages = set.toList())
    }

    fun setAppDataHeavy(packageName: String, heavy: Boolean) = update { s ->
        val set = s.dataHeavyPackages.toMutableSet()
        if (heavy) set.add(packageName) else set.remove(packageName)
        s.copy(dataHeavyPackages = set.toList())
    }

    fun saveSchedule(schedule: ScheduleDto) {
        viewModelScope.launch {
            store.updateSettings { s ->
                val others = s.schedules.filter { it.id != schedule.id }
                s.copy(schedules = others + schedule)
            }
            scheduleCoordinator.evaluate()
        }
    }

    fun deleteSchedule(id: String) {
        viewModelScope.launch {
            store.updateSettings { s -> s.copy(schedules = s.schedules.filter { it.id != id }) }
            scheduleCoordinator.evaluate()
        }
    }

    fun toggleSchedule(id: String, enabled: Boolean) {
        viewModelScope.launch {
            store.updateSettings { s ->
                s.copy(schedules = s.schedules.map { if (it.id == id) it.copy(enabled = enabled) else it })
            }
            scheduleCoordinator.evaluate()
        }
    }

    fun remainingNow(): Long? = engine.remainingNowMs()

    fun setFastTime(on: Boolean) {
        DebugHooks.setFastTime(on)
    }

    fun setupOwnerHardening() = deviceOwner.applyHardening()

    /** Loads the launchable apps (Dispatchers.IO, so the list does not block the UI). */
    fun loadApps() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val resolved = pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL)
                resolved
                    .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                    .filter { it.packageName != context.packageName }
                    .distinctBy { it.packageName }
                    .sortedBy { it.label.lowercase() }
            }
            _apps.value = list
        }
    }

    suspend fun currentSettings(): SettingsDto = store.currentSettings()

    companion object {
        const val MAX_CONTACTS = 3
    }
}
