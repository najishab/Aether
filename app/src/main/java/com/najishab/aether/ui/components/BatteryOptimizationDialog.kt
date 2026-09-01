package com.najishab.aether.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.najishab.aether.R

/** True when the OS is already allowed to keep this app awake in the background. */
fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

/**
 * Builds the system intent that opens the "ignore battery optimizations"
 * consent dialog for this app. Falls back to the app's own battery settings
 * page if the direct-request intent isn't handled (some OEM ROMs strip it).
 */
fun batteryOptimizationIntent(context: Context): Intent {
    val direct = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}"),
    )
    val resolves = direct.resolveActivity(context.packageManager) != null
    return if (resolves) direct else Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}

/**
 * Explains why we're about to show the system battery-exemption dialog
 * before actually showing it — a bare system popup right after connecting
 * is confusing, and asking cold also risks Play policy scrutiny.
 */
@Composable
fun BatteryOptimizationDialog(
    onAllow: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.battery_opt_title)) },
        text = { Text(text = stringResource(R.string.battery_opt_message)) },
        confirmButton = {
            TextButton(onClick = onAllow) {
                Text(text = stringResource(R.string.battery_opt_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.battery_opt_later))
            }
        },
    )
}
