package com.sandpause.app.data.repo

import com.sandpause.app.data.db.EventDao
import com.sandpause.app.data.db.EventEntity
import com.sandpause.app.lock.AppClock
import javax.inject.Inject
import javax.inject.Singleton

enum class EventType {
    LOCK_STARTED,
    LOCK_ENDED,
    LOCK_TIME_ADDED,
    TAP,
    PATIENCE,
    WRONG_PIN,
    PIN_COOLDOWN,
    UNLOCK_BY_PARENT,
    BLOCKED_APP,
    PLAY_STORE_PIN_PROMPT,
    CLOCK_CHANGED,
    ACCESSIBILITY_OFF,
    ACCESSIBILITY_ON,
    SERVICE_RESTARTED,
    BOOT_RESTORED,
    ADMIN_DISABLE_ATTEMPT,
    DATA_BUDGET_EXCEEDED,
    ALLOWANCE_REACHED,
    REWARD,
    EMERGENCY_CALL,
}

@Singleton
class EventLogger @Inject constructor(
    private val events: EventDao,
    private val clock: AppClock,
) {
    suspend fun log(type: EventType, detail: String = "") {
        events.insert(EventEntity(at = clock.wallMs(), type = type.name, detail = detail))
    }

    /** Removes events older than 90 days so the database stays small. */
    suspend fun pruneOld() {
        events.deleteBefore(clock.wallMs() - 90L * 24 * 60 * 60 * 1000)
    }
}
