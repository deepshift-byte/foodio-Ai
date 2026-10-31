package com.sandpause.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sandpause.app.R
import com.sandpause.app.security.PinManager
import com.sandpause.core.security.PinPolicy
import kotlinx.coroutines.launch

private sealed interface PinFeedback {
    data class Wrong(val attemptsLeft: Int) : PinFeedback
    data class CoolingDown(val remainingMs: Long) : PinFeedback
}

/**
 * A 4-to-6-digit PIN pad. [verify] checks the PIN, and wrong attempts and cooldowns
 * come back as feedback. The pad is locked while [verify] runs.
 */
@Composable
fun PinEntry(
    verify: suspend (String) -> PinManager.VerifyResult,
    onVerified: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pin by rememberSaveable { mutableStateOf("") }
    var feedback by remember { mutableStateOf<PinFeedback?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (busy || !PinPolicy.isValidPin(pin)) return
        busy = true
        val attempt = pin
        scope.launch {
            when (val result = verify(attempt)) {
                PinManager.VerifyResult.Ok -> onVerified()
                is PinManager.VerifyResult.Wrong -> {
                    feedback = PinFeedback.Wrong(result.attemptsLeft)
                    pin = ""
                }
                is PinManager.VerifyResult.CoolingDown -> {
                    feedback = PinFeedback.CoolingDown(result.remainingMs)
                    pin = ""
                }
            }
            busy = false
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PinDots(filled = pin.length)
        val current = feedback
        val text = when (current) {
            null -> stringResource(R.string.pin_prompt)
            is PinFeedback.Wrong -> pluralStringResource(
                R.plurals.gate_wrong_pin,
                current.attemptsLeft,
                current.attemptsLeft,
            )
            is PinFeedback.CoolingDown -> stringResource(R.string.gate_cooldown, formatMmSs(current.remainingMs))
        }
        Text(
            text,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = if (current == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        )
        PinPad(
            onDigit = { digit ->
                if (!busy && pin.length < 6) {
                    pin += digit
                    feedback = null
                }
            },
            onBackspace = { pin = pin.dropLast(1) },
            onSubmit = ::submit,
            submitEnabled = !busy && pin.length >= 4,
        )
    }
}

/** Full-screen PIN prompt used over another app (for example before the Play Store opens). */
@Composable
fun ParentGateDialog(
    verify: suspend (String) -> PinManager.VerifyResult,
    onVerified: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(R.string.gate_playstore_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            CenteredHint(stringResource(R.string.gate_playstore_body))
            VerticalSpace(16)
            PinEntry(verify = verify, onVerified = onVerified)
            VerticalSpace(12)
            TextLink(stringResource(R.string.action_cancel), onCancel)
        }
    }
}
