package com.sandpause.app.security

import com.sandpause.app.data.repo.EventLogger
import com.sandpause.app.data.repo.EventType
import com.sandpause.app.data.settings.SettingsStore
import com.sandpause.app.data.settings.toCore
import com.sandpause.app.data.settings.toDto
import com.sandpause.app.lock.AppClock
import com.sandpause.core.security.AttemptLimiter
import com.sandpause.core.security.PinHasher
import com.sandpause.core.security.PinPolicy
import javax.inject.Inject
import javax.inject.Singleton

/** Remembers that a parent has entered the PIN recently, so the parent area does not ask again at once. */
@Singleton
class ParentSession @Inject constructor(private val clock: AppClock) {
    @Volatile private var verifiedUntilElapsed: Long = -1L
    @Volatile private var verifiedBootId: String = ""

    fun markVerified() {
        verifiedUntilElapsed = clock.elapsedMs() + WINDOW_MS
        verifiedBootId = clock.bootId()
    }

    fun isVerified(): Boolean {
        if (verifiedUntilElapsed < 0) return false
        val now = clock.elapsedMs()
        val sameBoot = verifiedBootId.isEmpty() || verifiedBootId == clock.bootId()
        return sameBoot && now <= verifiedUntilElapsed
    }

    fun clear() {
        verifiedUntilElapsed = -1L
    }

    companion object {
        const val WINDOW_MS = 2 * 60_000L
    }
}

@Singleton
class PinManager @Inject constructor(
    private val store: SettingsStore,
    private val clock: AppClock,
    private val events: EventLogger,
    private val parentSession: ParentSession,
) {
    sealed interface VerifyResult {
        data object Ok : VerifyResult
        data class Wrong(val attemptsLeft: Int) : VerifyResult
        data class CoolingDown(val remainingMs: Long) : VerifyResult
    }

    suspend fun verify(pin: String): VerifyResult {
        val settings = store.currentSettings()
        val now = clock.elapsedMs()
        val boot = clock.bootId()
        val state = settings.attempts.toCore()

        val waiting = AttemptLimiter.remainingCooldownMs(state, now, boot)
        if (waiting > 0) return VerifyResult.CoolingDown(waiting)

        if (PinHasher.verify(pin, settings.pinHash)) {
            store.updateSettings { it.copy(attempts = AttemptLimiter.onCorrectAttempt().toDto()) }
            parentSession.markVerified()
            return VerifyResult.Ok
        }

        val next = AttemptLimiter.onWrongAttempt(state, now, boot)
        store.updateSettings { it.copy(attempts = next.toDto()) }
        events.log(EventType.WRONG_PIN)
        val cooldown = AttemptLimiter.remainingCooldownMs(next, now, boot)
        return if (cooldown > 0) {
            events.log(EventType.PIN_COOLDOWN, "${cooldown / 60_000} min")
            VerifyResult.CoolingDown(cooldown)
        } else {
            VerifyResult.Wrong(AttemptLimiter.attemptsLeft(next))
        }
    }

    /** First-run setup. Saves the PIN and the recovery question and answer, hashed. */
    suspend fun savePinAndRecovery(pin: String, question: String, answer: String) {
        require(PinPolicy.isValidPin(pin)) { "PIN must be 4 to 6 digits" }
        store.updateSettings {
            it.copy(
                pinHash = PinHasher.hash(pin),
                recoveryQuestion = question.trim(),
                recoveryAnswerHash = PinHasher.hash(PinPolicy.normalizeAnswer(answer)),
                attempts = AttemptLimiter.onCorrectAttempt().toDto(),
            )
        }
        parentSession.markVerified()
    }

    /**
     * Changes the PIN after a correct recovery answer. Wrong answers share the
     * same throttling as the PIN, so the answer cannot be guessed around it.
     */
    suspend fun resetWithRecoveryAnswer(answer: String, newPin: String): VerifyResult {
        require(PinPolicy.isValidPin(newPin)) { "PIN must be 4 to 6 digits" }
        val settings = store.currentSettings()
        val now = clock.elapsedMs()
        val boot = clock.bootId()
        val state = settings.attempts.toCore()

        val waiting = AttemptLimiter.remainingCooldownMs(state, now, boot)
        if (waiting > 0) return VerifyResult.CoolingDown(waiting)

        if (!PinHasher.verify(PinPolicy.normalizeAnswer(answer), settings.recoveryAnswerHash)) {
            val next = AttemptLimiter.onWrongAttempt(state, now, boot)
            store.updateSettings { it.copy(attempts = next.toDto()) }
            events.log(EventType.WRONG_PIN, "recovery")
            val cooldown = AttemptLimiter.remainingCooldownMs(next, now, boot)
            return if (cooldown > 0) VerifyResult.CoolingDown(cooldown)
            else VerifyResult.Wrong(AttemptLimiter.attemptsLeft(next))
        }
        store.updateSettings {
            it.copy(pinHash = PinHasher.hash(newPin), attempts = AttemptLimiter.onCorrectAttempt().toDto())
        }
        parentSession.markVerified()
        return VerifyResult.Ok
    }
}
