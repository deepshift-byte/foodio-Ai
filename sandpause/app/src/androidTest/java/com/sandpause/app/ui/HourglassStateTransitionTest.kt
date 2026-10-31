package com.sandpause.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sandpause.app.ui.child.Hourglass
import com.sandpause.app.ui.theme.SandPauseTheme
import com.sandpause.core.hourglass.HourglassMachine
import com.sandpause.core.hourglass.HourglassState
import com.sandpause.core.hourglass.Mood
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks the yellow -> red -> yellow -> green transitions through the real
 * Hourglass composable. Taps are real touch input. Time comes from a fake clock,
 * so the five-second red phase is exact and nothing waits in real time.
 */
@RunWith(AndroidJUnit4::class)
class HourglassStateTransitionTest {

    @get:Rule
    val rule = createComposeRule()

    private val totalMs = 10 * 60_000L

    @Test
    fun yellowRedYellowGreen() {
        var state by mutableStateOf(HourglassState())
        var remainingMs by mutableLongStateOf(totalMs)
        var fakeNowMs by mutableLongStateOf(0L)

        rule.setContent {
            SandPauseTheme {
                HourglassHost(
                    state = state,
                    remainingMs = remainingMs,
                    totalMs = totalMs,
                    nowMs = { fakeNowMs },
                    onTap = { now -> state = HourglassMachine.onTap(state, now, remainingMs).first },
                )
            }
        }

        assertMood(Mood.RESTING)

        // Five taps inside one minute turn the hourglass red.
        for (t in listOf(0L, 10_000L, 20_000L, 30_000L, 40_000L)) {
            rule.runOnIdle { fakeNowMs = t }
            tap()
        }
        assertMood(Mood.IMPATIENT)

        // Still red at 4 s, back to yellow at 5 s.
        rule.runOnIdle { state = HourglassMachine.onTick(state, nowMs = 44_000L, remainingMs = remainingMs) }
        assertMood(Mood.IMPATIENT)
        rule.runOnIdle { state = HourglassMachine.onTick(state, nowMs = 45_000L, remainingMs = remainingMs) }
        assertMood(Mood.RESTING)

        // The last grain drops: green.
        rule.runOnIdle {
            remainingMs = 0L
            state = HourglassMachine.onTick(state, nowMs = 600_000L, remainingMs = remainingMs)
        }
        assertMood(Mood.FINISHED)
    }

    @Test
    fun singleTapStaysYellow() {
        var state by mutableStateOf(HourglassState())
        var fakeNowMs by mutableLongStateOf(1_000L)

        rule.setContent {
            SandPauseTheme {
                HourglassHost(
                    state = state,
                    remainingMs = totalMs,
                    totalMs = totalMs,
                    nowMs = { fakeNowMs },
                    onTap = { now -> state = HourglassMachine.onTap(state, now, totalMs).first },
                )
            }
        }

        tap()
        assertMood(Mood.RESTING)
    }

    private fun tap() {
        rule.onNode(hasStateDescription(Mood.RESTING.name).or(hasStateDescription(Mood.IMPATIENT.name)))
            .performTouchInput { click() }
        rule.waitForIdle()
    }

    private fun assertMood(mood: Mood) {
        rule.onNode(hasStateDescription(mood.name)).fetchSemanticsNode()
    }
}

@Composable
private fun HourglassHost(
    state: HourglassState,
    remainingMs: Long,
    totalMs: Long,
    nowMs: () -> Long,
    onTap: (Long) -> Unit,
) {
    Hourglass(
        mood = state.mood,
        fraction = { remainingMs.toFloat() / totalMs.toFloat() },
        onTap = { onTap(nowMs()) },
        modifier = Modifier,
    )
}
