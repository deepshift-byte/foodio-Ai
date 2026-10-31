package com.sandpause.app.lock

import com.sandpause.app.data.repo.EventLogger
import com.sandpause.app.data.repo.EventType
import com.sandpause.app.data.settings.ContactDto
import com.sandpause.app.data.settings.SettingsStore
import com.sandpause.app.di.ApplicationScope
import com.sandpause.app.voice.VoiceAnnouncer
import com.sandpause.core.hourglass.HourglassMachine
import com.sandpause.core.hourglass.HourglassState
import com.sandpause.core.hourglass.Mood
import com.sandpause.core.hourglass.TapOutcome
import com.sandpause.core.hourglass.VoiceLine
import com.sandpause.core.hourglass.VoiceLines
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the lock screen shows. Built from the engine, settings and hourglass state. */
data class ChildUi(
    val childName: String,
    val remainingMs: Long,
    val totalMs: Long,
    val mood: Mood,
    val caption: String?,
    val suggestions: List<String>,
    val contacts: List<ContactDto>,
) {
    val finished: Boolean get() = mood == Mood.FINISHED
}

/**
 * Shared by the lock screen activity and the fallback overlay. Holds the
 * hourglass mood, handles taps, and turns engine state into [ChildUi].
 */
@Singleton
class ChildLockCoordinator @Inject constructor(
    private val engine: LockEngine,
    private val store: SettingsStore,
    private val voice: VoiceAnnouncer,
    private val events: EventLogger,
    private val clock: AppClock,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _hourglass = MutableStateFlow(HourglassState())

    /** Emits about four times a second while a lock is running, and never otherwise. */
    private val ticks: Flow<Long> = engine.session.flatMapLatest { session ->
        if (session == null) emptyFlow() else flow {
            while (true) {
                emit(clock.elapsedMs())
                delay(TICK_MS)
            }
        }
    }

    val ui: StateFlow<ChildUi?> = combine(
        engine.session,
        store.settings,
        _hourglass,
        voice.caption,
        ticks.onStart { emit(0L) },
    ) { session, settings, hourglass, caption, _ ->
        if (session == null) return@combine null
        val now = clock.elapsedMs()
        val boot = clock.bootId()
        ChildUi(
            childName = settings.childName,
            remainingMs = session.remainingMs(now, boot),
            totalMs = session.totalMs,
            mood = hourglass.mood,
            caption = caption,
            suggestions = settings.suggestions,
            contacts = settings.contacts.take(MAX_PARENT_CONTACTS),
        )
    }.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        // Advance the mood on every tick. A new lock starts back at yellow.
        scope.launch {
            ticks.collect { now ->
                val remaining = engine.remainingNowMs() ?: return@collect
                _hourglass.value = HourglassMachine.onTick(_hourglass.value, now, remaining)
            }
        }
        scope.launch {
            engine.session.collect { session ->
                if (session != null && _hourglass.value.mood == Mood.FINISHED) {
                    _hourglass.value = HourglassState()
                }
            }
        }
    }

    /** Fraction of the lock left, read by the hourglass on every frame. */
    fun fractionNow(): Float = engine.fractionNow()

    fun onHourglassTap() {
        val remaining = engine.remainingNowMs() ?: return
        val now = clock.elapsedMs()
        val (next, outcome) = HourglassMachine.onTap(_hourglass.value, now, remaining)
        _hourglass.value = next
        scope.launch { events.log(EventType.TAP) }
        when (outcome) {
            TapOutcome.SpeakRemaining -> voice.speak(VoiceLines.forRemaining(remaining))
            TapOutcome.Patience -> {
                scope.launch { events.log(EventType.PATIENCE) }
                voice.speak(VoiceLine.Patience, bypassCooldown = true)
            }
            TapOutcome.Silent, TapOutcome.Ignored -> Unit
        }
    }

    companion object {
        const val TICK_MS = 250L
        const val MAX_PARENT_CONTACTS = 3
    }
}
