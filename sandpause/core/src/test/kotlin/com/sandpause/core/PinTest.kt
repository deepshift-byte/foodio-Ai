package com.sandpause.core

import com.sandpause.core.security.AttemptLimiter
import com.sandpause.core.security.PinHasher
import com.sandpause.core.security.PinPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinTest {

    @Test
    fun pinIsFourToSixDigits() {
        assertTrue(PinPolicy.isValidPin("1234"))
        assertTrue(PinPolicy.isValidPin("123456"))
        assertFalse(PinPolicy.isValidPin("123"))
        assertFalse(PinPolicy.isValidPin("1234567"))
        assertFalse(PinPolicy.isValidPin("12a4"))
        assertFalse(PinPolicy.isValidPin(""))
    }

    @Test
    fun hashNeverContainsThePlainPin() {
        val stored = PinHasher.hash("4821", iterations = 1_000, salt = ByteArray(16) { 7 })
        assertFalse(stored.contains("4821"))
        assertTrue(stored.startsWith("pbkdf2_sha256\$"))
    }

    @Test
    fun verifyAcceptsOnlyTheRightPin() {
        val stored = PinHasher.hash("4821")
        assertTrue(PinHasher.verify("4821", stored))
        assertFalse(PinHasher.verify("4822", stored))
        assertFalse(PinHasher.verify("", stored))
    }

    @Test
    fun sameSecretGetsDifferentSaltEachTime() {
        val a = PinHasher.hash("1111", iterations = 1_000)
        val b = PinHasher.hash("1111", iterations = 1_000)
        assertNotEquals("salt must be random", a, b)
        assertTrue(PinHasher.verify("1111", a))
        assertTrue(PinHasher.verify("1111", b))
    }

    @Test
    fun malformedOrMissingHashNeverVerifies() {
        assertFalse(PinHasher.verify("1234", null))
        assertFalse(PinHasher.verify("1234", ""))
        assertFalse(PinHasher.verify("1234", "garbage"))
        assertFalse(PinHasher.verify("1234", "pbkdf2_sha256\$x\$y\$z"))
        assertFalse(PinHasher.verify("1234", "md5\$120000\$AAAA\$BBBB"))
    }

    @Test
    fun recoveryAnswersIgnoreCaseAndExtraSpaces() {
        assertEquals("kampala road", PinPolicy.normalizeAnswer("  Kampala    Road "))
    }

    @Test
    fun cooldownDoublesAfterEachLockout() {
        assertEquals(5L, AttemptLimiter.cooldownMinutesFor(1))
        assertEquals(10L, AttemptLimiter.cooldownMinutesFor(2))
        assertEquals(20L, AttemptLimiter.cooldownMinutesFor(3))
        assertEquals(40L, AttemptLimiter.cooldownMinutesFor(4))
        assertEquals(AttemptLimiter.MAX_COOLDOWN_MINUTES, AttemptLimiter.cooldownMinutesFor(30))
    }

    @Test
    fun fifthWrongPinStartsFiveMinuteCooldown() {
        val boot = "b"
        var s = com.sandpause.core.security.AttemptState()
        repeat(4) {
            s = AttemptLimiter.onWrongAttempt(s, 0, boot)
            assertFalse(AttemptLimiter.isCoolingDown(s, 0, boot))
        }
        assertEquals(1, AttemptLimiter.attemptsLeft(s))
        s = AttemptLimiter.onWrongAttempt(s, 0, boot)
        assertTrue(AttemptLimiter.isCoolingDown(s, 0, boot))
        assertEquals(5 * 60_000L, AttemptLimiter.remainingCooldownMs(s, 0, boot))
        assertFalse("cooldown ends after 5 minutes", AttemptLimiter.isCoolingDown(s, 5 * 60_000L, boot))
    }

    @Test
    fun secondLockoutDoublesToTenMinutes() {
        val boot = "b"
        var s = com.sandpause.core.security.AttemptState()
        repeat(5) { s = AttemptLimiter.onWrongAttempt(s, 0, boot) }
        // Wait out the first cooldown, then fail five more times.
        repeat(5) { s = AttemptLimiter.onWrongAttempt(s, 6 * 60_000L, boot) }
        assertEquals(10 * 60_000L, AttemptLimiter.remainingCooldownMs(s, 6 * 60_000L, boot))
    }

    @Test
    fun correctPinResetsEverything() {
        val boot = "b"
        var s = com.sandpause.core.security.AttemptState()
        repeat(5) { s = AttemptLimiter.onWrongAttempt(s, 0, boot) }
        s = AttemptLimiter.onCorrectAttempt()
        assertEquals(0, s.lockouts)
        assertEquals(5, AttemptLimiter.attemptsLeft(s))
    }
}
