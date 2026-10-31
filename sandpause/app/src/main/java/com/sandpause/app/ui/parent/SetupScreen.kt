package com.sandpause.app.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sandpause.app.R
import com.sandpause.app.data.settings.AppLanguage
import com.sandpause.app.ui.common.CenteredHint
import com.sandpause.app.ui.common.PinDots
import com.sandpause.app.ui.common.PinPad
import com.sandpause.app.ui.common.PrimaryButton
import com.sandpause.app.ui.common.VerticalSpace
import com.sandpause.app.ui.common.TextLink
import com.sandpause.core.security.PinPolicy
import kotlinx.coroutines.launch

/** First-run setup: language, child's name, PIN (entered twice), recovery question. */
@Composable
fun SetupScreen(
    viewModel: ParentViewModel,
    onDone: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var language by rememberSaveable { mutableStateOf(AppLanguage.ENGLISH.name) }
    var childName by rememberSaveable { mutableStateOf("") }
    var firstPin by rememberSaveable { mutableStateOf("") }
    var pinMismatch by rememberSaveable { mutableStateOf(false) }
    var question by rememberSaveable { mutableStateOf("") }
    var answer by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.setup_step_count, step + 1, STEPS),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (step) {
            0 -> {
                Text(stringResource(R.string.setup_welcome_title), style = MaterialTheme.typography.headlineMedium)
                CenteredHint(stringResource(R.string.setup_welcome_body))
                VerticalSpace(8)
                Text(stringResource(R.string.setup_language), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LanguageChoice(
                        label = stringResource(R.string.setup_language_english),
                        selected = language == AppLanguage.ENGLISH.name,
                    ) { language = AppLanguage.ENGLISH.name }
                    LanguageChoice(
                        label = stringResource(R.string.setup_language_luganda),
                        selected = language == AppLanguage.LUGANDA.name,
                    ) { language = AppLanguage.LUGANDA.name }
                }
                VerticalSpace(8)
                PrimaryButton(stringResource(R.string.action_continue)) { step = 1 }
            }

            1 -> {
                Text(stringResource(R.string.setup_name_title), style = MaterialTheme.typography.headlineMedium)
                CenteredHint(stringResource(R.string.setup_name_hint))
                OutlinedTextField(
                    value = childName,
                    onValueChange = { childName = it.take(30) },
                    label = { Text(stringResource(R.string.setup_child_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                PrimaryButton(stringResource(R.string.action_continue), enabled = childName.isNotBlank()) { step = 2 }
                TextLink(stringResource(R.string.action_back)) { step = 0 }
            }

            2, 3 -> key(step) {
                val confirming = step == 3
                Text(
                    stringResource(if (confirming) R.string.setup_pin_confirm_title else R.string.setup_pin_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                CenteredHint(stringResource(if (confirming) R.string.setup_pin_confirm_body else R.string.setup_pin_body))
                if (pinMismatch && confirming) {
                    Text(stringResource(R.string.setup_pin_mismatch), color = MaterialTheme.colorScheme.error)
                }
                PinCreate(
                    onSubmit = { entered ->
                        if (!confirming) {
                            firstPin = entered
                            pinMismatch = false
                            step = 3
                        } else if (entered == firstPin) {
                            pinMismatch = false
                            step = 4
                        } else {
                            pinMismatch = true
                            firstPin = ""
                            step = 2
                        }
                    },
                )
                TextLink(stringResource(R.string.action_back)) { step = if (confirming) 2 else 1 }
            }

            else -> {
                Text(stringResource(R.string.setup_recovery_title), style = MaterialTheme.typography.headlineMedium)
                CenteredHint(stringResource(R.string.setup_recovery_body))
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it.take(80) },
                    label = { Text(stringResource(R.string.setup_recovery_question)) },
                    placeholder = { Text(stringResource(R.string.setup_recovery_question_example)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it.take(60) },
                    label = { Text(stringResource(R.string.setup_recovery_answer)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                PrimaryButton(
                    text = stringResource(R.string.setup_finish),
                    enabled = !saving && question.isNotBlank() && PinPolicy.normalizeAnswer(answer).length >= 2,
                ) {
                    saving = true
                    scope.launch {
                        viewModel.completeSetup(
                            childName = childName,
                            language = AppLanguage.valueOf(language),
                            pin = firstPin,
                            question = question,
                            answer = answer,
                        )
                        onDone()
                    }
                }
                TextLink(stringResource(R.string.action_back)) { step = 3 }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private const val STEPS = 5

@Composable
private fun LanguageChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) { Text(label) }
    }
}

/** A PIN keypad that collects 4 to 6 digits and sends them to [onSubmit] when the tick is pressed. */
@Composable
private fun PinCreate(onSubmit: (String) -> Unit) {
    var entry by rememberSaveable { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PinDots(filled = entry.length)
        PinPad(
            onDigit = { if (entry.length < 6) entry += it },
            onBackspace = { entry = entry.dropLast(1) },
            onSubmit = {
                if (PinPolicy.isValidPin(entry)) {
                    onSubmit(entry)
                    entry = ""
                }
            },
            submitEnabled = entry.length >= 4,
        )
    }
}

@Composable
fun GateScreen(
    viewModel: ParentViewModel,
    onUnlocked: () -> Unit,
    onForgot: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.gate_title), style = MaterialTheme.typography.headlineMedium)
        CenteredHint(stringResource(R.string.gate_subtitle))
        VerticalSpace(16)
        com.sandpause.app.ui.common.PinEntry(
            verify = { viewModel.verifyPin(it) },
            onVerified = onUnlocked,
        )
        VerticalSpace(8)
        TextLink(stringResource(R.string.gate_forgot), onForgot)
    }
}

@Composable
fun RecoveryScreen(
    viewModel: ParentViewModel,
    onRecovered: () -> Unit,
) {
    var answer by rememberSaveable { mutableStateOf("") }
    var newPin by rememberSaveable { mutableStateOf("") }
    var confirmPin by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val question = viewModel.recoveryQuestion()
    val context = androidx.compose.ui.platform.LocalContext.current
    val wrongAnswerText = stringResource(R.string.recovery_wrong_answer)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.recovery_title), style = MaterialTheme.typography.headlineMedium)
        CenteredHint(stringResource(R.string.recovery_body))
        Text(
            stringResource(R.string.recovery_question_label, question),
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it.take(60) },
            label = { Text(stringResource(R.string.recovery_answer)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = newPin,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) newPin = it },
            label = { Text(stringResource(R.string.recovery_new_pin)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = confirmPin,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) confirmPin = it },
            label = { Text(stringResource(R.string.setup_pin_confirm_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        PrimaryButton(
            text = stringResource(R.string.recovery_reset),
            enabled = !busy && answer.isNotBlank() && PinPolicy.isValidPin(newPin) && newPin == confirmPin,
        ) {
            busy = true
            scope.launch {
                when (val result = viewModel.recoverPin(answer, newPin)) {
                    com.sandpause.app.security.PinManager.VerifyResult.Ok -> onRecovered()
                    is com.sandpause.app.security.PinManager.VerifyResult.Wrong -> message = wrongAnswerText
                    is com.sandpause.app.security.PinManager.VerifyResult.CoolingDown ->
                        message = context.getString(
                            R.string.gate_cooldown,
                            com.sandpause.app.ui.common.formatMmSs(result.remainingMs),
                        )
                }
                busy = false
            }
        }
    }
}
