package com.sandpause.app.ui.parent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sandpause.app.R
import com.sandpause.app.admin.Permissions
import com.sandpause.app.ui.common.CenteredHint
import com.sandpause.app.ui.common.SectionCard

/** Full protection (device owner) setup with the exact adb command, plus battery guidance for OEM phones. */
@Composable
fun DeviceOwnerScreen(viewModel: ParentViewModel) {
    val context = LocalContext.current
    val isOwner = viewModel.isDeviceOwner
    val oemSteps = stringArrayResource(oemStepsFor(Build.MANUFACTURER))
    val ownerSteps = stringArrayResource(R.array.owner_steps)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.owner_title), style = MaterialTheme.typography.headlineMedium)
        CenteredHint(stringResource(R.string.owner_intro))

        SectionCard(stringResource(R.string.owner_status_title)) {
            Text(
                if (isOwner) stringResource(R.string.owner_status_yes) else stringResource(R.string.owner_status_no),
                style = MaterialTheme.typography.titleMedium,
                color = if (isOwner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Text(stringResource(R.string.owner_fallback_note), style = MaterialTheme.typography.bodyMedium)
        }

        SectionCard(stringResource(R.string.owner_adb_title)) {
            Text(stringResource(R.string.owner_adb_hint), style = MaterialTheme.typography.bodyMedium)
            SelectionContainer {
                Text(
                    ADB_COMMAND,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            OutlinedButton(onClick = { copyToClipboard(context, ADB_COMMAND) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.owner_copy))
            }
            ownerSteps.forEachIndexed { index, step ->
                Text("${index + 1}. $step", style = MaterialTheme.typography.bodyMedium)
            }
            if (isOwner) {
                Button(onClick = { viewModel.setupOwnerHardening() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.owner_apply_hardening))
                }
            }
        }

        SectionCard(stringResource(R.string.owner_battery_title)) {
            Text(stringResource(R.string.owner_battery_hint), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(oemTitleFor(Build.MANUFACTURER)),
                style = MaterialTheme.typography.titleMedium,
            )
            oemSteps.forEach { step ->
                Text("• $step", style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(
                onClick = {
                    openAppDetails(context)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.owner_open_app_details)) }
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.perm_open_settings)) }
        }
    }
}

const val ADB_COMMAND = "adb shell dpm set-device-owner com.sandpause.app/.admin.SandPauseAdminReceiver"

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("adb command", text))
}

private fun openAppDetails(context: Context) {
    openSettings(context, Settings.ACTION_APPLICATION_DETAILS_SETTINGS, withPackage = true)
}

/** Picks the OEM guidance for the phone. Transsion (Tecno, Infinix, Itel) and Xiaomi have the strictest battery killers. */
internal fun oemStepsFor(manufacturer: String): Int = when (manufacturer.lowercase()) {
    "tecno", "infinix", "itel", "transsion" -> R.array.oem_transsion_steps
    "xiaomi", "redmi", "poco" -> R.array.oem_xiaomi_steps
    "samsung" -> R.array.oem_samsung_steps
    else -> R.array.oem_generic_steps
}

internal fun oemTitleFor(manufacturer: String): Int = when (manufacturer.lowercase()) {
    "tecno", "infinix", "itel", "transsion" -> R.string.owner_oem_transsion
    "xiaomi", "redmi", "poco" -> R.string.owner_oem_xiaomi
    "samsung" -> R.string.owner_oem_samsung
    else -> R.string.owner_oem_generic
}
