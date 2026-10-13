package com.vespelabs.fixit.core.ui.permission

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vespelabs.fixit.core.ui.R

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION,
)

private enum class PermissionDialog { NONE, RATIONALE, DENIED, PERMANENTLY_DENIED }

/**
 * Location permission flow (location spec, "Permisos de ubicación"): [content] receives the action for the
 * "Usar mi ubicación" button, and nothing is requested until it runs. [onGranted] runs once coarse or fine
 * location is granted (an approximate grant is enough). Shows a rationale before asking when the system says so,
 * an explanatory message after a denial and, after a permanent denial, a dialog that opens the app settings.
 * The address stays editable meanwhile; the screen still requires coordinates to publish.
 */
@Composable
fun LocationPermissionGate(
    onGranted: () -> Unit,
    content: @Composable (requestLocation: () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val currentOnGranted by rememberUpdatedState(onGranted)
    var dialog by rememberSaveable { mutableStateOf(PermissionDialog.NONE) }

    val launcher = rememberLauncherForActivityResult(RequestMultiplePermissions()) { results ->
        dialog = when {
            results.isEmpty() -> PermissionDialog.NONE // request interrupted, nothing was answered
            context.hasLocationPermission() -> {
                currentOnGranted()
                PermissionDialog.NONE
            }
            // Right after a denial the system only stops offering the rationale when it will not ask again.
            activity?.shouldShowLocationRationale() == true -> PermissionDialog.DENIED
            else -> PermissionDialog.PERMANENTLY_DENIED
        }
    }

    content {
        when {
            context.hasLocationPermission() -> currentOnGranted()
            activity?.shouldShowLocationRationale() == true -> dialog = PermissionDialog.RATIONALE
            else -> launcher.launch(LOCATION_PERMISSIONS)
        }
    }

    val dismiss = { dialog = PermissionDialog.NONE }
    when (dialog) {
        PermissionDialog.NONE -> Unit
        PermissionDialog.RATIONALE -> PermissionAlert(
            title = stringResource(R.string.location_permission_title),
            text = stringResource(R.string.location_permission_rationale),
            confirmLabel = stringResource(R.string.location_permission_continue),
            onConfirm = {
                dismiss()
                launcher.launch(LOCATION_PERMISSIONS)
            },
            onDismiss = dismiss,
        )
        PermissionDialog.DENIED -> PermissionAlert(
            title = stringResource(R.string.location_permission_denied_title),
            text = stringResource(R.string.location_permission_denied),
            confirmLabel = stringResource(R.string.location_permission_ok),
            onConfirm = dismiss,
            onDismiss = null,
        )
        PermissionDialog.PERMANENTLY_DENIED -> PermissionAlert(
            title = stringResource(R.string.location_permission_denied_title),
            text = stringResource(R.string.location_permission_permanently_denied),
            confirmLabel = stringResource(R.string.location_permission_open_settings),
            onConfirm = {
                dismiss()
                context.openAppSettings()
            },
            onDismiss = dismiss,
        )
    }
}

/** [onDismiss] null -> single-button message that closes with [onConfirm]. */
@Composable
private fun PermissionAlert(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: (() -> Unit)?,
) {
    AlertDialog(
        onDismissRequest = onDismiss ?: onConfirm,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = onDismiss?.let {
            { TextButton(onClick = it) { Text(stringResource(R.string.location_permission_cancel)) } }
        },
    )
}

private fun Context.hasLocationPermission(): Boolean =
    LOCATION_PERMISSIONS.any { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

private fun Activity.shouldShowLocationRationale(): Boolean =
    LOCATION_PERMISSIONS.any { shouldShowRequestPermissionRationale(it) }

private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No settings screen on this device: the dialog already explained how to enable the permission.
    }
}
