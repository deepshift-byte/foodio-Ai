package com.sandpause.app.ui.parent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sandpause.app.R
import com.sandpause.app.ui.common.CenteredHint

/** Choose which apps stay usable during a lock, and which are "data-heavy". */
@Composable
fun AppsScreen(viewModel: ParentViewModel) {
    val apps by viewModel.apps.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.loadApps() }

    val allowed = settings.allowedPackages.toSet()
    val heavy = settings.dataHeavyPackages.toSet()
    val visible = apps.filter { it.label.contains(query, ignoreCase = true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.apps_title), style = MaterialTheme.typography.headlineMedium)
        CenteredHint(stringResource(R.string.apps_hint))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.apps_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (apps.isEmpty()) {
            CenteredHint(stringResource(R.string.apps_loading))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(visible, key = { it.packageName }) { app ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge)
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(
                        modifier = Modifier.clickable(role = Role.Checkbox) {
                            viewModel.setAppAllowed(app.packageName, app.packageName !in allowed)
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = app.packageName in allowed, onCheckedChange = null)
                        Text(stringResource(R.string.apps_allowed), style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = app.packageName in heavy,
                            onCheckedChange = { viewModel.setAppDataHeavy(app.packageName, it) },
                        )
                        Text(stringResource(R.string.apps_data_heavy), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}
