package com.vespelabs.fixit.core.ui.components

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.vespelabs.fixit.core.ui.R
import com.vespelabs.fixit.core.ui.theme.FixItTheme
import java.io.File

/**
 * "Galería" and "Cámara" plus the counter. The system launchers live here; the caller only
 * receives the picked URIs as strings through [onPhotosPicked] (never more than the missing ones,
 * never an empty list). No storage or camera permission is requested: the Photo Picker and the
 * system camera app run outside the process. "Cámara" is hidden on devices without a camera.
 */
@Composable
fun PhotoPickerButtons(
    photoCount: Int,
    maxPhotos: Int,
    onPhotosPicked: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val missing = (maxPhotos - photoCount).coerceAtLeast(0)
    val canAdd = enabled && missing > 0
    // Previews have no FileProvider nor camera, so the camera button is shown there by default.
    val inPreview = LocalInspectionMode.current
    val hasCamera = remember(context) {
        inPreview || context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    fun deliver(uris: List<Uri>) {
        // Some pickers may return more than requested, so the UI enforces the limit too.
        val picked = uris.take(missing).map(Uri::toString)
        if (picked.isNotEmpty()) onPhotosPicked(picked)
    }

    val pickSingle = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        deliver(listOfNotNull(uri))
    }
    // PickMultipleVisualMedia requires a maximum of at least 2, so it is only used when 2+ are missing.
    val pickMultiple = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(missing.coerceAtLeast(2)),
    ) { uris -> deliver(uris) }

    // Saved so the target survives the activity being recreated while the camera app is open.
    var pendingCaptureUri by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingCaptureUri
        pendingCaptureUri = null
        if (saved && uri != null) deliver(listOf(Uri.parse(uri)))
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = {
                val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                if (missing == 1) pickSingle.launch(request) else pickMultiple.launch(request)
            },
            enabled = canAdd,
        ) {
            Text(stringResource(R.string.photo_gallery))
        }
        if (hasCamera) {
            OutlinedButton(
                onClick = {
                    val uri = newCaptureUri(context)
                    pendingCaptureUri = uri.toString()
                    takePicture.launch(uri)
                },
                enabled = canAdd,
            ) {
                Text(stringResource(R.string.photo_camera))
            }
        }
        Text(
            text = stringResource(R.string.photo_count, photoCount, maxPhotos),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
    }
}

/**
 * A new empty file under `cacheDir/photos/` exposed through the app's FileProvider
 * (`${applicationId}.fileprovider`, declared in the `:app` manifest with `res/xml/file_paths.xml`).
 */
private fun newCaptureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "photos").apply { mkdirs() }
    val file = File.createTempFile("capture_", ".jpg", dir)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Preview(showBackground = true)
@Composable
private fun PhotoPickerButtonsPreview() {
    FixItTheme { PhotoPickerButtons(photoCount = 2, maxPhotos = 3, onPhotosPicked = {}) }
}
