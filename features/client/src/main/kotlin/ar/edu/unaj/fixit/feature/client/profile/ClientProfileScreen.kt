package ar.edu.unaj.fixit.feature.client.profile

import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ar.edu.unaj.fixit.core.ui.components.PlaceholderScreen
import ar.edu.unaj.fixit.core.ui.theme.FixItTheme
import ar.edu.unaj.fixit.feature.client.R
import ar.edu.unaj.fixit.core.ui.R as UiR

/**
 * Root of the "Perfil" tab. Sign out lives here (the session gate in `:app` reacts by itself).
 * TODO(task 1.8.10): replace the placeholder body with the real profile (UiState from the ViewModel).
 */
@Composable
fun ClientProfileRoute(
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClientProfileViewModel = hiltViewModel(),
) {
    ClientProfileScreen(onEditClick = onEditClick, onSignOutClick = viewModel::onSignOutClick, modifier = modifier)
}

@Composable
fun ClientProfileScreen(
    onEditClick: () -> Unit,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(title = stringResource(R.string.client_profile_title), taskRef = "1.8.10", modifier = modifier) {
        Button(onClick = onEditClick) { Text(stringResource(R.string.client_placeholder_edit_profile)) }
        OutlinedButton(onClick = onSignOutClick) { Text(stringResource(UiR.string.common_sign_out)) }
    }
}

@Preview(showBackground = true)
@Composable
private fun ClientProfileScreenPreview() {
    FixItTheme { ClientProfileScreen(onEditClick = {}, onSignOutClick = {}) }
}
