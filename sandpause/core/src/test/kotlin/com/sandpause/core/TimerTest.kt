package com.sandpause.core

import com.sandpause.core.lock.LockReason
import com.sandpause.core.lock.LockSession
import com.sandpause.core.time.ClockGuard
import com.sandpause.core.time.ElapsedDeadline
import com.sandpause.core.time.TimeScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerTest {

    private val boot = "boot-A"

    @Test
    fun countsDownOnTheElapsedClock() {
        val d = ElapsedDeadline.start(60_000, nowElapsedMs = 1_000, nowBootId = boot)
        assertEquals(60_000, d.remainingAt(1_000, boot))
        assertEquals(40_000, d.remainingAt(21_000, boot))
        assertEquals(0, d.remainingAt(61_000, boot))
        assertEquals("never negative", 0L, d.remainingAt(999_999, boot))
    }

    @Test
    fun processRestartInSameBootLosesNoTime() {
        // Checkpoint, then the process dies and comes back 20 s later on the same boot.
        val saved = ElapsedDeadline.start(60_000, nowElapsedMs = 0, nowBootId = boot)
        assertEquals(40_000, saved.remainingAt(20_000, boot))
    }

    @Test
    fun rebootKeepsStoredRemainingAndNeverShortensIt() {
        val saved = ElapsedDeadline.start(60_000, nowElapsedMs = 5_000, nowBootId = boot)
        // New boot: elapsed clock restarted and the boot id differs.
        assertEquals(60_000, saved.remainingAt(nowElapsedMs = 2_000, nowBootId = "boot-B"))
        assertEquals(60_000, saved.remainingAt(nowElapsedMs = 9_000, nowBootId = "boot-B"))
    }

    @Test
    fun elapsedGoingBackwardsIsTreatedAsReboot() {
        val saved = ElapsedDeadline.start(30_000, nowElapsedMs = 90_000, nowBootId = "")
        assertEquals(30_000, saved.remainingAt(nowElapsedMs = 1_000, nowBootId = ""))
    }

    @Test
    fun extendingAddsToRemainingFromNow() {
        val d = ElapsedDeadline.start(60_000, 0, boot).extendedBy(10_000, nowElapsedMs = 30_000, nowBootId = boot)
        assertEquals(40_000, d.remainingAt(30_000, boot))
        assertEquals(10_000, d.remainingAt(60_000, boot))
    }

    @Test
    fun rebasedKeepsRemainingAtTheMomentOfRebasing() {
        val d = ElapsedDeadline.start(60_000, 0, boot).rebased(nowElapsedMs = 15_000, nowBootId = boot)
        assertEquals(45_000, d.remainingMs)
        assertEquals(15_000, d.anchorElapsedMs)
    }

    @Test
    fun lockSessionFractionDrivesHourglass() {
        val s = LockSession.start(1, LockReason.MANUAL, wallMs = 0, durationMs = 100_000, nowElapsedMs = 0, nowBootId = boot)
        assertEquals(1f, s.fraction(0, boot), 0.0001f)
        assertEquals(0.5f, s.fraction(50_000, boot), 0.0001f)
        assertEquals(0f, s.fraction(100_000, boot), 0.0001f)
        assertTrue(s.isFinished(100_000, boot))
    }

    @Test
    fun addingTimeRefillsTheSandProportionally() {
        val s = LockSession.start(1, LockReason.MANUAL, 0, 100_000, 0, boot)
            .addTime(100_000, nowElapsedMs = 50_000, nowBootId = boot)
        // 50 s were left, plus 100 s, over a total of 200 s.
        assertEquals(150_000, s.remainingMs(50_000, boot))
        assertEquals(200_000L, s.totalMs)
        assertEquals(0.75f, s.fraction(50_000, boot), 0.0001f)
    }

    @Test
    fun clockJumpIsDetected() {
        // Wall clock moved forward 2 hours while 10 s of monotonic time passed.
        assertTrue(ClockGuard.isWallClockJump(0, 0, 2 * 3_600_000L, 10_000))
        // Normal progress is not a jump.
        assertFalse(ClockGuard.isWallClockJump(1_000_000, 500, 1_010_500, 10_500))
        // Small jitter inside the tolerance is ignored.
        assertFalse(ClockGuard.isWallClockJump(0, 0, 10_900, 10_000))
        // A reboot (elapsed went backwards) is not a clock change.
        assertFalse(ClockGuard.isWallClockJump(0, 500_000, 90_000, 1_000))
    }

    @Test
    fun fastTimeMakesOneMinuteFiveSeconds() {
        assertEquals(60_000L, TimeScale.minutesToMs(1, fastTime = false))
        assertEquals(5_000L, TimeScale.minutesToMs(1, fastTime = true))
        assertEquals(30 * 5_000L, TimeScale.minutesToMs(30, fastTime = true))
        assertEquals(3, TimeScale.msToMinutesCeil(150_000, fastTime = false))
        assertEquals(2, TimeScale.msToMinutesCeil(90_000, fastTime = false))
    }
}
