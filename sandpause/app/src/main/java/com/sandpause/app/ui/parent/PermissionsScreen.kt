package com.sandpause.app.ui.parent

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sandpause.app.R
import com.sandpause.app.admin.Permissions
import com.sandpause.app.ui.common.CenteredHint
import com.sandpause.app.ui.common.PrimaryButton
import com.sandpause.app.ui.common.SectionCard

/**
 * Permission onboarding with child-friendly explanations. The Accessibility use
 * needs the prominent disclosure and the consent switch first, as Google Play policy requires.
 */
@Composable
fun PermissionsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Recomputed on every resume, so a permission granted in Settings shows up here at once.
    val status = remember(refresh) { PermissionStatus.read(context) }
    var disclosureAccepted by rememberSaveable { mutableStateOf(false) }

    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refresh++ }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.perm_title), style = MaterialTheme.typography.headlineMedium)
        CenteredHint(stringResource(R.string.perm_intro))

        PermissionCard(
            title = stringResource(R.string.perm_overlay_title),
            why = stringResource(R.string.perm_overlay_why),
            granted = status.overlay,
            actionLabel = stringResource(R.string.perm_open_settings),
        ) { openSettings(context, Settings.ACTION_MANAGE_OVERLAY_PERMISSION, withPackage = true) }

        PermissionCard(
            title = stringResource(R.string.perm_usage_title),
            why = stringResource(R.string.perm_usage_why),
            granted = status.usage,
            actionLabel = stringResource(R.string.perm_open_settings),
        ) { openSettings(context, Settings.ACTION_USAGE_ACCESS_SETTINGS) }

        SectionCard(stringResource(R.string.perm_accessibility_title)) {
            Text(stringResource(R.string.perm_accessibility_why), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.perm_disclosure_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(stringResource(R.string.perm_disclosure_body), style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = disclosureAccepted, onCheckedChange = { disclosureAccepted = it })
                Text(stringResource(R.string.perm_disclosure_agree), modifier = Modifier.padding(start = 10.dp))
            }
            Text(
                if (status.accessibility) stringResource(R.string.perm_granted) else stringResource(R.string.perm_missing),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                onClick = { openSettings(context, Settings.ACTION_ACCESSIBILITY_SETTINGS) },
                enabled = disclosureAccepted,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.perm_open_settings)) }
        }

        PermissionCard(
            title = stringResource(R.string.perm_exact_alarm_title),
            why = stringResource(R.string.perm_exact_alarm_why),
            granted = status.exactAlarms,
            actionLabel = stringResource(R.string.perm_open_settings),
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                openSettings(context, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, withPackage = true)
            }
        }

        PermissionCard(
            title = stringResource(R.string.perm_battery_title),
            why = stringResource(R.string.perm_battery_why),
            granted = status.battery,
            actionLabel = stringResource(R.string.perm_open_settings),
        ) { openSettings(context, Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }

        PermissionCard(
            title = stringResource(R.string.perm_calls_title),
            why = stringResource(R.string.perm_calls_why),
            granted = status.calls,
            actionLabel = stringResource(R.string.perm_allow),
        ) { runtimeLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE)) }

        PermissionCard(
            title = stringResource(R.string.perm_notifications_title),
            why = stringResource(R.string.perm_notifications_why),
            granted = status.notifications,
            actionLabel = stringResource(R.string.perm_allow),
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                runtimeLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }

        PrimaryButton(stringResource(R.string.action_continue), onClick = onDone)
    }
}

@Composable
private fun PermissionCard(
    title: String,
    why: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    SectionCard(title) {
        Text(why, style = MaterialTheme.typography.bodyMedium)
        Text(
            if (granted) stringResource(R.string.perm_granted) else stringResource(R.string.perm_missing),
            style = MaterialTheme.typography.titleMedium,
            color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        if (!granted) {
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
        }
    }
}

private data class PermissionStatus(
    val overlay: Boolean,
    val usage: Boolean,
    val accessibility: Boolean,
    val exactAlarms: Boolean,
    val battery: Boolean,
    val calls: Boolean,
    val notifications: Boolean,
) {
    companion object {
        fun read(context: Context) = PermissionStatus(
            overlay = Permissions.canDrawOverlays(context),
            usage = Permissions.hasUsageAccess(context),
            accessibility = Permissions.hasAccessibility(context),
            exactAlarms = Permissions.canScheduleExactAlarms(context),
            battery = Permissions.isIgnoringBatteryOptimizations(context),
            calls = Permissions.hasCallPermission(context),
            notifications = Permissions.hasNotificationPermission(context),
        )
    }
}

/** Opens a system settings screen. Some OEM builds lack a screen, so failures are ignored. */
internal fun openSettings(context: Context, action: String, withPackage: Boolean = false) {
    val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (withPackage) intent.data = Uri.parse("package:${context.packageName}")
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // The screen is not available on this device.
    }
}
