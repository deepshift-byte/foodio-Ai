package com.sandpause.core

import com.sandpause.core.policy.AccessPolicy
import com.sandpause.core.policy.KnownPackages
import com.sandpause.core.policy.PolicyInput
import com.sandpause.core.policy.Verdict
import com.sandpause.core.schedule.Allowance
import com.sandpause.core.schedule.LockSchedule
import com.sandpause.core.schedule.ScheduleEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class ScheduleTest {

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int): LocalDateTime =
        LocalDateTime.of(y, m, d, h, min)

    // 2026-11-02 is a Monday.
    private val homework = LockSchedule(
        id = "hw", name = "Homework", days = ScheduleEvaluator.SCHOOL_DAYS,
        startMinute = 16 * 60, endMinute = 17 * 60,
    )
    private val bedtime = LockSchedule(
        id = "bed", name = "Bedtime", days = ScheduleEvaluator.EVERY_DAY,
        startMinute = 20 * 60, endMinute = 6 * 60 + 30,
    )

    @Test
    fun windowIsActiveOnlyInsideItsRangeAndDays() {
        assertNull(ScheduleEvaluator.activeAt(listOf(homework), at(2026, 11, 2, 15, 59)))
        val inside = ScheduleEvaluator.activeAt(listOf(homework), at(2026, 11, 2, 16, 0))
        assertEquals("hw", inside?.schedule?.id)
        assertEquals(at(2026, 11, 2, 17, 0), inside?.endsAt)
        assertNull("end minute is exclusive", ScheduleEvaluator.activeAt(listOf(homework), at(2026, 11, 2, 17, 0)))
        assertNull("Saturday is not a school day", ScheduleEvaluator.activeAt(listOf(homework), at(2026, 11, 7, 16, 30)))
    }

    @Test
    fun overnightBedtimeIsActiveBeforeMidnightAndAfterMidnight() {
        val late = ScheduleEvaluator.activeAt(listOf(bedtime), at(2026, 11, 2, 21, 0))
        assertEquals(at(2026, 11, 3, 6, 30), late?.endsAt)
        val early = ScheduleEvaluator.activeAt(listOf(bedtime), at(2026, 11, 3, 2, 0))
        assertEquals(at(2026, 11, 3, 6, 30), early?.endsAt)
        assertNull("not active at 07:00", ScheduleEvaluator.activeAt(listOf(bedtime), at(2026, 11, 3, 7, 0)))
    }

    @Test
    fun overnightWindowOnlyCountsOnItsStartDayAfterMidnight() {
        // A bedtime window that starts Sunday 20:00 runs into Monday morning, not Sunday morning.
        val sunOnly = bedtime.copy(days = setOf(DayOfWeek.SUNDAY))
        assertNull("Sunday 02:00 belongs to Saturday's window, which is not set", ScheduleEvaluator.activeAt(listOf(sunOnly), at(2026, 11, 8, 2, 0)))
        assertNotNull("Monday 02:00 is inside Sunday's window", ScheduleEvaluator.activeAt(listOf(sunOnly), at(2026, 11, 9, 2, 0)))
    }

    @Test
    fun latestEndingWindowWins() {
        val short = LockSchedule("a", "A", ScheduleEvaluator.EVERY_DAY, 9 * 60, 10 * 60)
        val long = LockSchedule("b", "B", ScheduleEvaluator.EVERY_DAY, 9 * 60, 12 * 60)
        assertEquals("b", ScheduleEvaluator.activeAt(listOf(short, long), at(2026, 11, 2, 9, 30))?.schedule?.id)
    }

    @Test
    fun disabledOrInvalidSchedulesAreIgnored() {
        assertNull(ScheduleEvaluator.activeAt(listOf(homework.copy(enabled = false)), at(2026, 11, 2, 16, 30)))
        val empty = homework.copy(days = emptySet())
        assertNull(ScheduleEvaluator.activeAt(listOf(empty), at(2026, 11, 2, 16, 30)))
        assertFalse(homework.copy(endMinute = homework.startMinute).isValid)
    }

    @Test
    fun nextStartFindsTheNearestUpcomingWindow() {
        // Monday 10:00: the next start is Monday 16:00 (homework).
        assertEquals(at(2026, 11, 2, 16, 0), ScheduleEvaluator.nextStart(listOf(homework), at(2026, 11, 2, 10, 0)))
        // Monday 17:30: next is Tuesday 16:00.
        assertEquals(at(2026, 11, 3, 16, 0), ScheduleEvaluator.nextStart(listOf(homework), at(2026, 11, 2, 17, 30)))
        // Friday evening: next is Monday 16:00 (weekend skipped).
        assertEquals(at(2026, 11, 9, 16, 0), ScheduleEvaluator.nextStart(listOf(homework), at(2026, 11, 6, 18, 0)))
        // Bedtime starts strictly after now.
        assertEquals(at(2026, 11, 2, 20, 0), ScheduleEvaluator.nextStart(listOf(bedtime), at(2026, 11, 2, 19, 59)))
    }

    @Test
    fun allowanceArithmetic() {
        val allowance = 60L * 60_000
        assertFalse(Allowance.isExhausted(allowance, bonusMsToday = 0, usedMsToday = allowance - 1))
        assertTrue(Allowance.isExhausted(allowance, bonusMsToday = 0, usedMsToday = allowance))
        assertFalse("bonus pushes the limit out", Allowance.isExhausted(allowance, 15 * 60_000L, allowance))
        assertFalse("0 means allowance is off", Allowance.isExhausted(0, 0, Long.MAX_VALUE / 2))
        assertEquals(
            30 * 60_000L,
            Allowance.msUntilMidnight(at(2026, 11, 2, 23, 30)),
        )
    }

    private fun policy(
        pkg: String,
        lockActive: Boolean = true,
        allowed: Set<String> = emptySet(),
        dataHeavy: Set<String> = emptySet(),
        budgetExceeded: Boolean = false,
        playStorePin: Boolean = false,
        verified: Boolean = false,
    ) = AccessPolicy.decide(
        PolicyInput(
            packageName = pkg,
            lockActive = lockActive,
            allowedPackages = allowed,
            dataHeavyPackages = dataHeavy,
            dataBudgetExceeded = budgetExceeded,
            playStoreNeedsPin = playStorePin,
            parentVerified = verified,
            ownPackage = "com.sandpause.app",
        ),
    )

    @Test
    fun duringLockOnlyAllowedAppsRun() {
        assertEquals(Verdict.ALLOW, policy("com.sandpause.app"))
        assertEquals(Verdict.ALLOW, policy("com.android.systemui"))
        assertEquals(Verdict.BLOCK, policy("com.example.game"))
        assertEquals(Verdict.ALLOW, policy("com.example.reader", allowed = setOf("com.example.reader")))
    }

    @Test
    fun playStoreBrowsersAndDialersAreBlockedEvenIfAllowlisted() {
        val allowed = setOf(KnownPackages.PLAY_STORE, "com.android.chrome", "com.google.android.dialer")
        assertEquals(Verdict.BLOCK, policy(KnownPackages.PLAY_STORE, allowed = allowed))
        assertEquals(Verdict.BLOCK, policy("com.android.chrome", allowed = allowed))
        assertEquals(Verdict.BLOCK, policy("com.google.android.dialer", allowed = allowed))
        assertEquals(Verdict.ALLOW, policy("com.android.server.telecom"))
    }

    @Test
    fun outsideLockEverythingRunsExceptBudgetedDataApps() {
        assertEquals(Verdict.ALLOW, policy("com.example.game", lockActive = false))
        assertEquals(Verdict.BLOCK, policy("com.example.video", lockActive = false, dataHeavy = setOf("com.example.video"), budgetExceeded = true))
        assertEquals(Verdict.ALLOW, policy("com.example.video", lockActive = false, dataHeavy = setOf("com.example.video"), budgetExceeded = false))
    }

    @Test
    fun playStoreNeedsParentPinUntilVerified() {
        assertEquals(Verdict.REQUIRE_PARENT_PIN, policy(KnownPackages.PLAY_STORE, lockActive = false, playStorePin = true))
        assertEquals(Verdict.ALLOW, policy(KnownPackages.PLAY_STORE, lockActive = false, playStorePin = true, verified = true))
    }
}
