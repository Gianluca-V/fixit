package com.vespelabs.fixit.feature.client.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vespelabs.fixit.core.domain.model.User
import com.vespelabs.fixit.core.domain.model.UserRole
import com.vespelabs.fixit.core.ui.components.ErrorContent
import com.vespelabs.fixit.core.ui.components.LoadingContent
import com.vespelabs.fixit.core.ui.components.ScreenScaffold
import com.vespelabs.fixit.core.ui.theme.FixItTheme
import com.vespelabs.fixit.feature.client.R
import com.vespelabs.fixit.core.ui.R as UiR

/** Root of the "Perfil" tab. Sign out lives here (the session gate in `:app` reacts by itself). */
@Composable
fun ClientProfileRoute(
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClientProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ClientProfileScreen(
        uiState = uiState,
        onEditClick = onEditClick,
        onSignOutClick = viewModel::onSignOutClick,
        modifier = modifier,
    )
}

@Composable
fun ClientProfileScreen(
    uiState: ClientProfileUiState,
    onEditClick: () -> Unit,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(title = stringResource(R.string.client_profile_title), modifier = modifier) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (uiState) {
            ClientProfileUiState.Loading -> LoadingContent(contentModifier)
            // Sign out stays reachable even when the profile cannot be read.
            is ClientProfileUiState.Error -> Column(contentModifier.fillMaxSize()) {
                ErrorContent(error = uiState.error, modifier = Modifier.weight(1f))
                SignOutButton(onSignOutClick, Modifier.padding(16.dp))
            }
            is ClientProfileUiState.Success -> ProfileDetails(
                user = uiState.user,
                onEditClick = onEditClick,
                onSignOutClick = onSignOutClick,
                modifier = contentModifier,
            )
        }
    }
}

@Composable
private fun ProfileDetails(
    user: User,
    onEditClick: () -> Unit,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProfileField(label = stringResource(UiR.string.profile_field_name), value = user.name)
        ProfileField(label = stringResource(R.string.client_profile_email), value = user.email)
        ProfileField(label = stringResource(UiR.string.profile_field_phone), value = user.phone)
        ProfileField(label = stringResource(R.string.client_profile_role), value = user.role.label())
        Button(onClick = onEditClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.client_profile_edit))
        }
        SignOutButton(onSignOutClick)
    }
}

@Composable
internal fun ProfileField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SignOutButton(onSignOutClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onSignOutClick, modifier = modifier.fillMaxWidth()) {
        Text(stringResource(UiR.string.common_sign_out))
    }
}

/** The role is fixed at sign-up; this screen only ever shows a client. */
@Composable
internal fun UserRole.label(): String = stringResource(
    when (this) {
        UserRole.CLIENT -> R.string.client_role_client
        UserRole.PROFESSIONAL -> R.string.client_role_professional
    },
)

@Preview(showBackground = true)
@Composable
private fun ClientProfileScreenPreview() {
    FixItTheme {
        ClientProfileScreen(
            uiState = ClientProfileUiState.Success(
                User(id = "c1", name = "Ana López", email = "ana@fixit.test", phone = "11 5555-0001", role = UserRole.CLIENT),
            ),
            onEditClick = {},
            onSignOutClick = {},
        )
    }
}
