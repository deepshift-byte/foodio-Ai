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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sandpause.app.R
import com.sandpause.app.ui.common.BarChart
import com.sandpause.app.ui.common.CenteredHint
import com.sandpause.app.ui.common.SectionCard
import java.time.format.TextStyle
import java.util.Locale

/** Daily and weekly screen time, data used, and activity counts. Read from the local database only. */
@Composable
fun ReportsScreen(viewModel: ParentViewModel) {
    val report by viewModel.report.collectAsState()
    val r = report
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.reports_title), style = MaterialTheme.typography.headlineMedium)
        if (r == null) {
            CenteredHint(stringResource(R.string.reports_loading))
            return@Column
        }

        SectionCard(stringResource(R.string.reports_today)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(stringResource(R.string.reports_screen_minutes), r.today.screenMinutes.toString(), Modifier.weight(1f))
                StatTile(stringResource(R.string.reports_data_mb), "%.1f".format(r.today.dataMb), Modifier.weight(1f))
            }
        }

        val dayLabels = r.week.map { it.day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }

        SectionCard(stringResource(R.string.reports_week_screen)) {
            Text(
                stringResource(R.string.reports_week_total, r.weekScreenMinutes),
                style = MaterialTheme.typography.bodyLarge,
            )
            BarChart(
                values = r.week.map { it.screenMinutes.toFloat() },
                labels = dayLabels,
                barColor = MaterialTheme.colorScheme.primary,
                contentDescription = stringResource(R.string.reports_week_screen),
            )
        }

        SectionCard(stringResource(R.string.reports_week_data)) {
            Text(
                stringResource(R.string.reports_week_data_total, "%.1f".format(r.weekDataMb)),
                style = MaterialTheme.typography.bodyLarge,
            )
            BarChart(
                values = r.week.map { it.dataMb },
                labels = dayLabels,
                barColor = Color(0xFFB7791F),
                contentDescription = stringResource(R.string.reports_week_data),
            )
        }

        SectionCard(stringResource(R.string.reports_activity)) {
            StatLine(stringResource(R.string.reports_locks_started), r.locksStarted)
            StatLine(stringResource(R.string.reports_locks_completed), r.locksCompleted)
            StatLine(stringResource(R.string.reports_taps), r.taps)
            StatLine(stringResource(R.string.reports_unlock_attempts), r.unlockAttempts)
            StatLine(stringResource(R.string.reports_blocked_attempts), r.blockedAppAttempts)
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatLine(label: String, value: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value.toString(), style = MaterialTheme.typography.titleMedium)
    }
}
