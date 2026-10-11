package com.vespelabs.fixit.feature.professional.profile

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.vespelabs.fixit.core.ui.components.PlaceholderScreen
import com.vespelabs.fixit.core.ui.theme.FixItTheme
import com.vespelabs.fixit.feature.professional.R

/**
 * "Editar perfil". `:app` resolves onSaved to goBackFrom(ProEditProfileKey).
 * TODO(task 1.9.9/1.9.10): add ProEditProfileViewModel (tests first) and the real form
 * (ProfileFormFields from :core:ui with showProfessionalFields = true).
 */
@Composable
fun ProEditProfileRoute(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ProEditProfileScreen(onBack = onBack, onSaved = onSaved, modifier = modifier)
}

@Composable
fun ProEditProfileScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        title = stringResource(R.string.pro_edit_profile_title),
        taskRef = "1.9.10",
        modifier = modifier,
        onBack = onBack,
    ) {
        Button(onClick = onSaved) { Text(stringResource(R.string.pro_placeholder_saved)) }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProEditProfileScreenPreview() {
    FixItTheme { ProEditProfileScreen(onBack = {}, onSaved = {}) }
}
