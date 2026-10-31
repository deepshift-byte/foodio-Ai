package com.sandpause.app.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sandpause.app.R
import com.sandpause.app.admin.Permissions
import com.sandpause.app.data.settings.AppLanguage
import com.sandpause.app.data.settings.ContactDto
import com.sandpause.app.debug.DebugHooks
import com.sandpause.app.ui.common.PrimaryButton
import com.sandpause.app.ui.common.SectionCard
import com.sandpause.app.ui.common.SwitchRow
import com.sandpause.app.ui.common.formatMmSs
import com.sandpause.app.ui.common.CenteredHint
import kotlinx.coroutines.delay
import com.sandpause.core.time.TimeScale

/** Parent home: quick lock, rewards, allowance, data budget, contacts and settings. */
@Composable
fun HomeScreen(
    viewModel: ParentViewModel,
    onNavigate: (String) -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    val session by viewModel.session.collectAsState()
    val context = LocalContext.current
    val defaults = stringArrayResource(R.array.suggestions_default).toList()

    // Ticks once a second while the status card is on screen, so the countdown stays current.
    val nowMs by produceState(initialValue = System.currentTimeMillis(), session) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(R.string.home_title, settings.childName.ifBlank { stringResource(R.string.home_child_fallback) }),
            style = MaterialTheme.typography.headlineMedium,
        )

        SectionCard(stringResource(R.string.home_status_title)) {
            val mode = if (viewModel.isDeviceOwner) {
                stringResource(R.string.home_mode_owner)
            } else if (Permissions.hasAccessibility(context)) {
                stringResource(R.string.home_mode_fallback)
            } else {
                stringResource(R.string.home_mode_missing)
            }
            Text(mode, style = MaterialTheme.typography.bodyLarge)
            // Reading nowMs here makes this block recompose once a second while a lock runs.
            val remaining = remember(nowMs) { viewModel.remainingNow() }
            if (session != null && remaining != null) {
                Text(
                    stringResource(R.string.home_lock_running, formatMmSs(remaining)),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton(onClick = viewModel::endLockNow, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_end_lock))
                }
            } else {
                Text(stringResource(R.string.home_no_lock), style = MaterialTheme.typography.bodyMedium)
            }
        }

        SectionCard(stringResource(R.string.home_quick_lock)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(15, 30, 60).forEach { minutes ->
                    Button(
                        onClick = { viewModel.startQuickLock(minutes) },
                        modifier = Modifier.weight(1f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    ) {
                        Text(stringResource(R.string.quick_minutes, minutes))
                    }
                }
            }
            CustomLockRow(onStart = { h, m -> viewModel.startCustomLock(h, m) })
        }

        SectionCard(stringResource(R.string.home_rewards)) {
            CenteredHint(stringResource(R.string.home_rewards_hint))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(10, 15, 30).forEach { minutes ->
                    OutlinedButton(
                        onClick = { viewModel.grantReward(minutes) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.reward_minutes, minutes))
                    }
                }
            }
        }

        SectionCard(stringResource(R.string.home_allowance)) {
            NumberSettingRow(
                label = stringResource(R.string.home_allowance_label),
                current = settings.dailyAllowanceMin,
                hint = stringResource(R.string.home_allowance_hint),
                onSave = viewModel::setDailyAllowance,
            )
        }

        SectionCard(stringResource(R.string.home_data_budget)) {
            NumberSettingRow(
                label = stringResource(R.string.home_data_budget_label),
                current = settings.dataBudgetMb,
                hint = stringResource(R.string.home_data_budget_hint),
                onSave = viewModel::setDataBudget,
            )
        }

        SectionCard(stringResource(R.string.home_play_store)) {
            SwitchRow(
                title = stringResource(R.string.home_play_store_pin),
                subtitle = stringResource(R.string.home_play_store_pin_hint),
                checked = settings.playStorePin,
                onCheckedChange = viewModel::setPlayStorePin,
            )
        }

        SectionCard(stringResource(R.string.home_contacts)) {
            CenteredHint(stringResource(R.string.home_contacts_hint))
            settings.contacts.forEachIndexed { index, contact ->
                ContactRow(contact = contact, onRemove = { viewModel.removeContact(index) })
            }
            if (settings.contacts.size < ParentViewModel.MAX_CONTACTS) {
                AddContactRow(onAdd = { name, number -> viewModel.addContact(name, number) })
            }
        }

        SectionCard(stringResource(R.string.home_suggestions)) {
            SuggestionsEditor(
                current = settings.suggestions.ifEmpty { defaults },
                onSave = viewModel::setSuggestions,
            )
        }

        SectionCard(stringResource(R.string.home_language)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val current = settings.language
                AppLanguage.entries.forEach { language ->
                    val label = stringResource(
                        if (language == AppLanguage.ENGLISH) R.string.setup_language_english else R.string.setup_language_luganda,
                    )
                    if (language == current) {
                        Button(onClick = {}, modifier = Modifier.weight(1f)) { Text(label) }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.update { it.copy(languageCode = language.code) } },
                            modifier = Modifier.weight(1f),
                        ) { Text(label) }
                    }
                }
            }
        }

        SectionCard(stringResource(R.string.home_more)) {
            NavButton(stringResource(R.string.nav_schedules)) { onNavigate(Routes.SCHEDULES) }
            NavButton(stringResource(R.string.nav_apps)) { onNavigate(Routes.APPS) }
            NavButton(stringResource(R.string.nav_reports)) { onNavigate(Routes.REPORTS) }
            NavButton(stringResource(R.string.nav_permissions)) { onNavigate(Routes.PERMISSIONS) }
            NavButton(stringResource(R.string.nav_owner)) { onNavigate(Routes.OWNER) }
        }

        if (DebugHooks.AVAILABLE) {
            SectionCard(stringResource(R.string.home_debug_title)) {
                SwitchRow(
                    title = stringResource(R.string.home_fast_time),
                    subtitle = stringResource(R.string.home_fast_time_hint, TimeScale.FAST_MS_PER_MINUTE / 1000),
                    checked = viewModel.fastTimeOn,
                    onCheckedChange = { viewModel.setFastTime(it) },
                )
            }
        }
    }
}

@Composable
private fun CustomLockRow(onStart: (Int, Int) -> Unit) {
    var hours by rememberSaveable { mutableStateOf("0") }
    var minutes by rememberSaveable { mutableStateOf("45") }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = hours,
            onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) hours = it },
            label = { Text(stringResource(R.string.custom_hours)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = minutes,
            onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) minutes = it },
            label = { Text(stringResource(R.string.custom_minutes)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { onStart(hours.toIntOrNull() ?: 0, minutes.toIntOrNull() ?: 0) },
            enabled = (hours.toIntOrNull() ?: 0) * 60 + (minutes.toIntOrNull() ?: 0) > 0,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(),
        ) { Text(stringResource(R.string.start_lock)) }
    }
}

@Composable
private fun NumberSettingRow(
    label: String,
    current: Int,
    hint: String,
    onSave: (Int) -> Unit,
) {
    var value by rememberSaveable(current) { mutableStateOf(current.toString()) }
    CenteredHint(hint)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.length <= 5 && it.all(Char::isDigit)) value = it },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        Button(onClick = { onSave(value.toIntOrNull() ?: 0) }) { Text(stringResource(R.string.action_save)) }
    }
}

@Composable
private fun ContactRow(contact: ContactDto, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(contact.name, style = MaterialTheme.typography.bodyLarge)
            Text(contact.number, style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedButton(onClick = onRemove) { Text(stringResource(R.string.action_remove)) }
    }
}

@Composable
private fun AddContactRow(onAdd: (String, String) -> Boolean) {
    var name by rememberSaveable { mutableStateOf("") }
    var number by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(30) },
        label = { Text(stringResource(R.string.contact_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = number,
        onValueChange = { number = it.filter { c -> c.isDigit() || c == '+' }.take(16) },
        label = { Text(stringResource(R.string.contact_number)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth(),
    )
    PrimaryButton(
        text = stringResource(R.string.contact_add),
        enabled = name.isNotBlank() && number.length >= 6,
    ) {
        if (onAdd(name, number)) {
            name = ""
            number = ""
        }
    }
}

@Composable
private fun SuggestionsEditor(current: List<String>, onSave: (List<String>) -> Unit) {
    var text by rememberSaveable(current) { mutableStateOf(current.joinToString("\n")) }
    CenteredHint(stringResource(R.string.home_suggestions_hint))
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.take(1_000) },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(
        onClick = { onSave(text.lines()) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.action_save)) }
}

@Composable
private fun NavButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
