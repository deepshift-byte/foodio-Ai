package com.sandpause.app.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sandpause.app.R
import com.sandpause.app.data.settings.ScheduleDto
import com.sandpause.app.ui.common.CenteredHint
import com.sandpause.app.ui.common.PrimaryButton
import com.sandpause.app.ui.common.SectionCard
import java.util.UUID

/** Recurring lock windows, for example homework, bedtime or school mornings. */
@Composable
fun SchedulesScreen(viewModel: ParentViewModel) {
    val settings by viewModel.settings.collectAsState()
    val dayNames = stringArrayResource(R.array.days_short)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.schedules_title), style = MaterialTheme.typography.headlineMedium)
        CenteredHint(stringResource(R.string.schedules_hint))

        if (settings.schedules.isEmpty()) {
            CenteredHint(stringResource(R.string.schedules_empty))
        }
        settings.schedules.forEach { schedule ->
            ScheduleCard(
                schedule = schedule,
                dayNames = dayNames,
                onToggle = { viewModel.toggleSchedule(schedule.id, it) },
                onDelete = { viewModel.deleteSchedule(schedule.id) },
            )
        }

        SectionCard(stringResource(R.string.schedules_add_title)) {
            Text(stringResource(R.string.schedules_presets), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = {
                    viewModel.saveSchedule(
                        ScheduleDto(
                            id = UUID.randomUUID().toString(),
                            name = "Homework",
                            days = listOf(1, 2, 3, 4, 5),
                            startMinute = 16 * 60,
                            endMinute = 17 * 60,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.schedules_preset_homework)) }
            OutlinedButton(
                onClick = {
                    viewModel.saveSchedule(
                        ScheduleDto(
                            id = UUID.randomUUID().toString(),
                            name = "Bedtime",
                            days = (1..7).toList(),
                            startMinute = 20 * 60,
                            endMinute = 6 * 60 + 30,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.schedules_preset_bedtime)) }
            OutlinedButton(
                onClick = {
                    viewModel.saveSchedule(
                        ScheduleDto(
                            id = UUID.randomUUID().toString(),
                            name = "School mornings",
                            days = listOf(1, 2, 3, 4, 5),
                            startMinute = 6 * 60 + 30,
                            endMinute = 7 * 60 + 45,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.schedules_preset_mornings)) }

            ScheduleForm(dayNames = dayNames, onSave = viewModel::saveSchedule)
        }
    }
}

@Composable
private fun ScheduleCard(
    schedule: ScheduleDto,
    dayNames: Array<String>,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    SectionCard(schedule.name) {
        val days = schedule.days.sorted().joinToString(", ") { dayNames[(it - 1).coerceIn(0, 6)] }
        Text(days, style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(
                R.string.schedule_times,
                formatHm(schedule.startMinute),
                formatHm(schedule.endMinute),
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = schedule.enabled, onCheckedChange = onToggle)
                Text(stringResource(R.string.schedule_enabled), modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
        }
    }
}

@Composable
private fun ScheduleForm(dayNames: Array<String>, onSave: (ScheduleDto) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var days by rememberSaveable { mutableStateOf(setOf(1, 2, 3, 4, 5)) }
    var start by rememberSaveable { mutableStateOf("16:00") }
    var end by rememberSaveable { mutableStateOf("17:00") }
    val startMinute = parseHm(start)
    val endMinute = parseHm(end)
    val valid = name.isNotBlank() && days.isNotEmpty() && startMinute != null && endMinute != null &&
        startMinute != endMinute

    Text(stringResource(R.string.schedules_custom), style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(30) },
        label = { Text(stringResource(R.string.schedule_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..7).forEach { dow ->
            FilterChip(
                selected = dow in days,
                onClick = { days = if (dow in days) days - dow else days + dow },
                label = { Text(dayNames[dow - 1].take(3)) },
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = start,
            onValueChange = { start = it.take(5) },
            label = { Text(stringResource(R.string.schedule_start)) },
            singleLine = true,
            isError = startMinute == null,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = end,
            onValueChange = { end = it.take(5) },
            label = { Text(stringResource(R.string.schedule_end)) },
            singleLine = true,
            isError = endMinute == null,
            modifier = Modifier.weight(1f),
        )
    }
    CenteredHint(stringResource(R.string.schedule_form_hint))
    PrimaryButton(stringResource(R.string.action_save), enabled = valid) {
        onSave(
            ScheduleDto(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                days = days.sorted(),
                startMinute = startMinute ?: 0,
                endMinute = endMinute ?: 0,
            ),
        )
        name = ""
    }
}

/** "HH:mm" to minutes after midnight. "24:00" is allowed as an end time. */
fun parseHm(text: String): Int? {
    val parts = text.trim().split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (m !in 0..59) return null
    val total = h * 60 + m
    return if (total in 0..1440) total else null
}

fun formatHm(minutes: Int): String {
    val clamped = minutes.coerceIn(0, 1440)
    return "%02d:%02d".format(clamped / 60, clamped % 60)
}
