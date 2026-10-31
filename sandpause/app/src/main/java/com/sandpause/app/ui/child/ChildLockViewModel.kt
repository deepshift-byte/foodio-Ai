package com.sandpause.app.ui.child

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandpause.app.data.repo.EventLogger
import com.sandpause.app.data.repo.EventType
import com.sandpause.app.lock.ChildLockCoordinator
import com.sandpause.app.lock.ChildUi
import com.sandpause.app.lock.LockEngine
import com.sandpause.app.security.PinManager
import com.sandpause.core.lock.LockSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChildLockViewModel @Inject constructor(
    private val coordinator: ChildLockCoordinator,
    private val engine: LockEngine,
    private val pinManager: PinManager,
    private val events: EventLogger,
) : ViewModel() {

    val ui: StateFlow<ChildUi?> = coordinator.ui

    val session: StateFlow<LockSession?> = engine.session

    fun fractionNow(): Float = coordinator.fractionNow()

    fun onHourglassTap() = coordinator.onHourglassTap()

    suspend fun verifyParentPin(pin: String): PinManager.VerifyResult = pinManager.verify(pin)

    fun endLockNow() {
        viewModelScope.launch { engine.endLock(early = true) }
    }

    fun addMinutes(minutes: Int) {
        viewModelScope.launch { engine.addMinutes(minutes) }
    }

    /** Records an emergency or parent-contact call. The call itself is placed by the screen. */
    fun recordCall(number: String) {
        viewModelScope.launch { events.log(EventType.EMERGENCY_CALL, number) }
    }
}
