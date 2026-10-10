package ar.edu.unaj.fixit.feature.client.profile

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import ar.edu.unaj.fixit.core.ui.components.PlaceholderScreen
import ar.edu.unaj.fixit.core.ui.theme.FixItTheme
import ar.edu.unaj.fixit.feature.client.R

/**
 * "Editar perfil". `:app` resolves onSaved to goBackFrom(ClientEditProfileKey).
 * TODO(task 1.8.11/1.8.12): add ClientEditProfileViewModel (tests first) and the real form
 * (ProfileFormFields from :core:ui; email and role read-only).
 */
@Composable
fun ClientEditProfileRoute(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ClientEditProfileScreen(onBack = onBack, onSaved = onSaved, modifier = modifier)
}

@Composable
fun ClientEditProfileScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        title = stringResource(R.string.client_edit_profile_title),
        taskRef = "1.8.12",
        modifier = modifier,
        onBack = onBack,
    ) {
        Button(onClick = onSaved) { Text(stringResource(R.string.client_placeholder_saved)) }
    }
}

@Preview(showBackground = true)
@Composable
private fun ClientEditProfileScreenPreview() {
    FixItTheme { ClientEditProfileScreen(onBack = {}, onSaved = {}) }
}
