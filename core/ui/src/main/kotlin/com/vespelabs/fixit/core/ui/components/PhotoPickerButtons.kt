package com.vespelabs.fixit.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.vespelabs.fixit.core.ui.R
import com.vespelabs.fixit.core.ui.theme.FixItTheme

/**
 * Phase 1: a single "add sample photo" button plus the counter.
 * Phase 3 adds the camera and Photo Picker buttons here without changing the callers.
 */
@Composable
fun PhotoPickerButtons(
    photoCount: Int,
    maxPhotos: Int,
    onAddSamplePhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onAddSamplePhoto, enabled = photoCount < maxPhotos) {
            Text(stringResource(R.string.photo_add_sample))
        }
        Text(
            text = stringResource(R.string.photo_count, photoCount, maxPhotos),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PhotoPickerButtonsPreview() {
    FixItTheme { PhotoPickerButtons(photoCount = 2, maxPhotos = 3, onAddSamplePhoto = {}) }
}
