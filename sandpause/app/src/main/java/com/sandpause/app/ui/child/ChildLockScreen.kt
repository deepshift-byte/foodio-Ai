package com.sandpause.app.ui.child

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sandpause.app.R
import com.sandpause.app.lock.CallLauncher
import com.sandpause.app.ui.common.PinEntry
import com.sandpause.app.ui.common.PrimaryButton
import com.sandpause.app.ui.theme.ChildPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CELEBRATION_MS = 2_500L

/**
 * The lock screen with its overlays: the emergency sheet and the parent PIN pad.
 * When the lock ends, a short celebration plays and the activity closes.
 */
@Composable
fun ChildLockScreen(
    onParentSettings: () -> Unit,
    onLockEnded: () -> Unit,
    viewModel: ChildLockViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    val session by viewModel.session.collectAsState()
    var seenLock by remember { mutableStateOf(session != null) }
    var showEmergency by rememberSaveable { mutableStateOf(false) }
    var showParentPad by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(session) {
        if (session != null) {
            seenLock = true
        } else if (seenLock) {
            delay(CELEBRATION_MS)
            onLockEnded()
        }
    }

    val current = ui
    if (current != null) {
        ChildLockContent(
            ui = current,
            fraction = viewModel::fractionNow,
            onHourglassTap = viewModel::onHourglassTap,
            onEmergency = { showEmergency = true },
            onParentPad = { showParentPad = true },
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Box(Modifier.fillMaxSize().background(ChildPalette.YellowTop))
    }

    if (showEmergency) {
        EmergencySheet(
            contacts = current?.contacts.orEmpty().map { it.name to it.number },
            onDismiss = { showEmergency = false },
            onCall = { number ->
                viewModel.recordCall(number)
                CallLauncher.call(context, number)
            },
        )
    }

    if (showParentPad) {
        ParentPadDialog(
            verify = { viewModel.verifyParentPin(it) },
            onEndLock = {
                showParentPad = false
                viewModel.endLockNow()
            },
            onAddMinutes = { minutes ->
                showParentPad = false
                viewModel.addMinutes(minutes)
            },
            onParentSettings = {
                showParentPad = false
                onParentSettings()
            },
            onDismiss = { showParentPad = false },
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun EmergencySheet(
    contacts: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onCall: (String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.child_emergency_title), style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.child_emergency_hint), style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            Button(
                onClick = { onCall("112") },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = ChildPalette.Emergency, contentColor = androidx.compose.ui.graphics.Color.White),
            ) {
                Text(stringResource(R.string.child_call_112), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(
                onClick = { onCall("999") },
                modifier = Modifier.fillMaxWidth().height(60.dp),
            ) {
                Text(stringResource(R.string.child_call_999), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            }
            contacts.forEach { (name, number) ->
                OutlinedButton(
                    onClick = { onCall(number) },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                ) {
                    Text(stringResource(R.string.child_call_parent, name), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ParentPadDialog(
    verify: suspend (String) -> com.sandpause.app.security.PinManager.VerifyResult,
    onEndLock: () -> Unit,
    onAddMinutes: (Int) -> Unit,
    onParentSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    var unlocked by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        title = { Text(stringResource(R.string.child_parent_pad_title)) },
        text = {
            if (!unlocked) {
                PinEntry(verify = verify, onVerified = { unlocked = true })
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PrimaryButton(stringResource(R.string.child_pad_end_lock), onClick = onEndLock)
                    OutlinedButton(onClick = { onAddMinutes(5) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.child_pad_add_5))
                    }
                    OutlinedButton(onClick = { onAddMinutes(10) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.child_pad_add_10))
                    }
                    OutlinedButton(onClick = onParentSettings, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.child_pad_settings))
                    }
                }
            }
        },
    )
}
