package com.sandpause.app.lock

import com.sandpause.app.data.db.LockSessionDao
import com.sandpause.app.data.db.LockSessionEntity
import com.sandpause.app.data.repo.EventLogger
import com.sandpause.app.data.repo.EventType
import com.sandpause.app.data.repo.UsageTracker
import com.sandpause.app.data.settings.LockSnapshotDto
import com.sandpause.app.data.settings.SettingsStore
import com.sandpause.app.debug.DebugHooks
import com.sandpause.core.lock.LockReason
import com.sandpause.core.lock.LockSession
import com.sandpause.core.hourglass.VoiceLines
import com.sandpause.core.schedule.Allowance
import com.sandpause.core.time.ClockGuard
import com.sandpause.core.time.ElapsedDeadline
import com.sandpause.core.time.TimeScale
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the running lock.
 *
 * - The countdown is an [ElapsedDeadline] on the monotonic clock, so changing the
 *   wall clock or time zone cannot shorten it.
 * - The deadline is checkpointed to DataStore every [CHECKPOINT_MS] and restored
 *   after a process death or reboot. A reboot never shortens the lock.
 * - [tick] is called by [LockService]. It emits milestone and finish events, does
 *   daily screen-time accounting, and starts the daily auto-lock when the allowance
 *   is used up.
 */
@Singleton
class LockEngine @Inject constructor(
    private val store: SettingsStore,
    private val sessions: LockSessionDao,
    private val events: EventLogger,
    private val clock: AppClock,
    private val usage: UsageTracker,
) {
    sealed interface LockEvent {
        data class Started(val reason: LockReason) : LockEvent
        data class Milestone(val minutes: Int) : LockEvent
        data object Finished : LockEvent
    }

    private val mutex = Mutex()

    private val _session = MutableStateFlow<LockSession?>(null)
    val session: StateFlow<LockSession?> = _session.asStateFlow()

    private val _lockEvents = MutableSharedFlow<LockEvent>(extraBufferCapacity = 32)
    val lockEvents: SharedFlow<LockEvent> = _lockEvents.asSharedFlow()

    private var restored = false
    private var lastCheckpointElapsed = 0L
    private var lastRemainingMs: Long? = null
    private var lastTickWallMs = 0L
    private var lastTickElapsedMs = 0L
    private var screenOn = true
    private var lastAccountingElapsed: Long? = null

    val isLocked: Boolean get() = _session.value != null

    /** Remaining time right now, read on the monotonic clock. Safe to call from a frame callback. */
    fun remainingNowMs(): Long? = _session.value?.remainingMs(clock.elapsedMs(), clock.bootId())

    fun fractionNow(): Float = _session.value?.fraction(clock.elapsedMs(), clock.bootId()) ?: 0f

    fun onScreenChanged(on: Boolean) {
        screenOn = on
    }

    /** Restores a saved lock once per process. */
    suspend fun restore() = mutex.withLock {
        if (restored) return@withLock
        restored = true
        val snap = store.currentLock()
        if (!snap.active) return@withLock

        val now = clock.elapsedMs()
        val boot = clock.bootId()
        val deadline = ElapsedDeadline(snap.remainingMs, snap.anchorElapsedMs, snap.bootId)
        val reason = runCatching { LockReason.valueOf(snap.reason) }.getOrDefault(LockReason.MANUAL)
        val session = LockSession(snap.id, reason, snap.startedWallMs, snap.totalMs, deadline)
        if (session.isFinished(now, boot)) {
            finishLocked(completed = true, early = false)
            return@withLock
        }
        _session.value = session.copy(deadline = deadline.rebased(now, boot))
        lastRemainingMs = session.remainingMs(now, boot)
        lastCheckpointElapsed = now
        persistLocked()
    }

    /** Starts a lock. While locked, a manual lock adds time instead. Returns the active session. */
    suspend fun startLock(durationMs: Long, reason: LockReason): LockSession? = mutex.withLock {
        startLockLocked(durationMs, reason)
    }

    /** A quick lock from the parent area, in minutes (1 minute = 5 s in fast-time debug mode). */
    suspend fun startManual(minutes: Int): LockSession? =
        startLock(TimeScale.minutesToMs(minutes, DebugHooks.fastTime), LockReason.MANUAL)

    /** Adds lock time from the lock screen's parent pad. */
    suspend fun addMinutes(minutes: Int) = mutex.withLock {
        addTimeLocked(TimeScale.minutesToMs(minutes, DebugHooks.fastTime), EventType.LOCK_TIME_ADDED)
    }

    /** Reward buttons: extend a running lock, or add to today's allowance when not locked. */
    suspend fun grantReward(minutes: Int) = mutex.withLock {
        val ms = TimeScale.minutesToMs(minutes, DebugHooks.fastTime)
        if (_session.value != null) {
            addTimeLocked(ms, EventType.REWARD)
        } else {
            usage.addBonusMs(ms)
            events.log(EventType.REWARD, "+${minutes} min (bonus for today)")
        }
    }

    /** Ends the lock now. [early] = a parent ended it from the PIN pad. */
    suspend fun endLock(early: Boolean) = mutex.withLock {
        if (_session.value != null) finishLocked(completed = !early, early = early)
    }

    /** Called about once a second while locked and every few seconds otherwise. */
    suspend fun tick() = mutex.withLock {
        val now = clock.elapsedMs()
        val boot = clock.bootId()
        val wall = clock.wallMs()

        if (lastTickWallMs != 0L &&
            ClockGuard.isWallClockJump(lastTickWallMs, lastTickElapsedMs, wall, now)
        ) {
            events.log(EventType.CLOCK_CHANGED, "wall clock changed; lock timer unaffected")
        }
        lastTickWallMs = wall
        lastTickElapsedMs = now

        val cur = _session.value
        if (cur != null) {
            lastAccountingElapsed = null
            val remaining = cur.remainingMs(now, boot)
            VoiceLines.milestonesCrossed(lastRemainingMs ?: remaining, remaining).forEach {
                _lockEvents.tryEmit(LockEvent.Milestone(it.minutes))
            }
            lastRemainingMs = remaining
            if (remaining <= 0L) {
                finishLocked(completed = true, early = false)
                return@withLock
            }
            if (now - lastCheckpointElapsed >= CHECKPOINT_MS) {
                lastCheckpointElapsed = now
                persistLocked()
            }
        } else {
            lastRemainingMs = null
            accountScreenTime(now)
            maybeStartAllowanceLock()
        }
    }

    private suspend fun accountScreenTime(now: Long) {
        val last = lastAccountingElapsed
        if (screenOn && last != null && now >= last) usage.addScreenMs(now - last)
        lastAccountingElapsed = if (screenOn) now else null
    }

    private suspend fun maybeStartAllowanceLock() {
        val settings = store.currentSettings()
        if (settings.dailyAllowanceMin <= 0) return
        val allowanceMs = TimeScale.minutesToMs(settings.dailyAllowanceMin, DebugHooks.fastTime)
        val used = usage.screenMsToday()
        val bonus = usage.bonusMsToday()
        if (!Allowance.isExhausted(allowanceMs, bonus, used)) return

        val lockMs = if (DebugHooks.fastTime) {
            TimeScale.minutesToMs(1, fastTime = true)
        } else {
            Allowance.msUntilMidnight(LocalDateTime.now())
        }
        events.log(EventType.ALLOWANCE_REACHED, "used ${used / 60_000} min")
        startLockLocked(lockMs, LockReason.ALLOWANCE)
    }

    private suspend fun startLockLocked(durationMs: Long, reason: LockReason): LockSession? {
        var current = _session.value
        if (current != null && current.isFinished(clock.elapsedMs(), clock.bootId())) {
            finishLocked(completed = true, early = false)
            current = null
        }
        if (current != null) {
            return if (reason == LockReason.MANUAL) {
                addTimeLocked(durationMs, EventType.LOCK_TIME_ADDED)
            } else {
                current // schedules and allowances never stack on a running lock
            }
        }
        if (durationMs <= 0L) return null

        val wall = clock.wallMs()
        val now = clock.elapsedMs()
        val boot = clock.bootId()
        val id = sessions.insert(
            LockSessionEntity(reason = reason.name, startedAt = wall, plannedMs = durationMs),
        )
        val session = LockSession.start(id, reason, wall, durationMs, now, boot)
        _session.value = session
        lastRemainingMs = durationMs
        lastCheckpointElapsed = now
        persistLocked()
        events.log(EventType.LOCK_STARTED, "${reason.name} ${durationMs / 1000}s")
        _lockEvents.tryEmit(LockEvent.Started(reason))
        return session
    }

    private suspend fun addTimeLocked(extraMs: Long, type: EventType): LockSession? {
        val cur = _session.value ?: return null
        val now = clock.elapsedMs()
        val boot = clock.bootId()
        val updated = cur.addTime(extraMs, now, boot)
        _session.value = updated
        lastRemainingMs = updated.remainingMs(now, boot)
        persistLocked()
        events.log(type, "+${extraMs / 1000}s")
        return updated
    }

    /** Must be called with [mutex] held. */
    private suspend fun finishLocked(completed: Boolean, early: Boolean) {
        val cur = _session.value ?: return
        val row = sessions.byId(cur.id)
        if (row != null) {
            sessions.update(row.copy(endedAt = clock.wallMs(), completed = completed, endedEarly = early))
        }
        _session.value = null
        lastRemainingMs = null
        persistLocked()
        events.log(
            if (early) EventType.UNLOCK_BY_PARENT else EventType.LOCK_ENDED,
            if (completed) "completed" else "ended by parent",
        )
        if (completed) _lockEvents.tryEmit(LockEvent.Finished)
    }

    private suspend fun persistLocked() {
        val cur = _session.value
        if (cur == null) {
            store.updateLock { LockSnapshotDto(active = false) }
            return
        }
        val now = clock.elapsedMs()
        val boot = clock.bootId()
        val d = cur.deadline.rebased(now, boot)
        store.updateLock {
            LockSnapshotDto(
                active = true,
                id = cur.id,
                reason = cur.reason.name,
                startedWallMs = cur.startedWallMs,
                totalMs = cur.totalMs,
                remainingMs = d.remainingMs,
                anchorElapsedMs = d.anchorElapsedMs,
                bootId = d.bootId,
            )
        }
    }

    companion object {
        const val CHECKPOINT_MS = 15_000L
    }
}
