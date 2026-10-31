package com.sandpause.core

import com.sandpause.core.hourglass.HourglassMachine
import com.sandpause.core.hourglass.HourglassState
import com.sandpause.core.hourglass.Mood
import com.sandpause.core.hourglass.TapOutcome
import com.sandpause.core.hourglass.VoiceLine
import com.sandpause.core.hourglass.VoiceLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HourglassTest {

    private val lockLeft = 10 * 60_000L

    /** Yellow -> red -> yellow -> green, the full transition the UI test also checks. */
    @Test
    fun yellowRedYellowGreenSequence() {
        var s = HourglassState()
        assertEquals(Mood.RESTING, s.mood)

        // Five taps inside one minute, starting at t = 0, 10 s, 20 s, 30 s, 40 s.
        var outcome: TapOutcome = TapOutcome.Silent
        for (t in listOf(0L, 10_000L, 20_000L, 30_000L, 40_000L)) {
            val (next, o) = HourglassMachine.onTap(s, t, lockLeft)
            s = next
            outcome = o
        }
        assertEquals(TapOutcome.Patience, outcome)
        assertEquals("turns red", Mood.IMPATIENT, s.mood)

        // Still red 4 s later, back to yellow once 5 s have passed.
        assertEquals(Mood.IMPATIENT, HourglassMachine.onTick(s, 44_000L, lockLeft).mood)
        s = HourglassMachine.onTick(s, 45_000L, lockLeft)
        assertEquals("returns to yellow", Mood.RESTING, s.mood)

        // The last grain drops: green.
        s = HourglassMachine.onTick(s, 50_000L, 0L)
        assertEquals("finished is green", Mood.FINISHED, s.mood)
    }

    @Test
    fun singleTapSpeaksTheRemainingTime() {
        val (_, outcome) = HourglassMachine.onTap(HourglassState(), 0, lockLeft)
        assertEquals(TapOutcome.SpeakRemaining, outcome)
    }

    @Test
    fun speechHasThreeSecondCooldown() {
        val (s1, o1) = HourglassMachine.onTap(HourglassState(), 1_000, lockLeft)
        assertEquals(TapOutcome.SpeakRemaining, o1)
        val (s2, o2) = HourglassMachine.onTap(s1, 3_000, lockLeft)
        assertEquals("2 s later is inside the cooldown", TapOutcome.Silent, o2)
        val (_, o3) = HourglassMachine.onTap(s2, 4_500, lockLeft)
        assertEquals(TapOutcome.SpeakRemaining, o3)
    }

    @Test
    fun oldTapsFallOutOfTheSixtySecondWindow() {
        var s = HourglassState()
        // Four taps spaced 20 s apart: only the last 60 s count, so five taps never accumulate.
        for (t in listOf(0L, 20_000L, 40_000L, 60_000L, 80_000L, 100_000L)) {
            s = HourglassMachine.onTap(s, t, lockLeft).first
        }
        assertEquals(Mood.RESTING, s.mood)
    }

    @Test
    fun tapsAfterFinishDoNothing() {
        val finished = HourglassState(mood = Mood.FINISHED)
        assertEquals(TapOutcome.Ignored, HourglassMachine.onTap(finished, 0, lockLeft).second)
    }

    @Test
    fun voiceLinesMatchTheRemainingTime() {
        assertEquals(VoiceLine.AlmostThere, VoiceLines.forRemaining(45_000))
        assertEquals(VoiceLine.Remaining(1), VoiceLines.forRemaining(60_000))
        assertEquals(VoiceLine.Remaining(3), VoiceLines.forRemaining(2 * 60_000 + 30_000))
    }

    @Test
    fun milestonesAreAnnouncedWhenCrossed() {
        val crossed = VoiceLines.milestonesCrossed(previousRemainingMs = 5 * 60_000 + 1_000, nowRemainingMs = 4 * 60_000)
        assertEquals(listOf(VoiceLine.Milestone(5)), crossed)
        assertEquals(
            listOf(VoiceLine.Milestone(2), VoiceLine.Milestone(1)),
            VoiceLines.milestonesCrossed(3 * 60_000, 30_000),
        )
        assertTrue(VoiceLines.milestonesCrossed(4 * 60_000, 3 * 60_000).isEmpty())
    }
}
