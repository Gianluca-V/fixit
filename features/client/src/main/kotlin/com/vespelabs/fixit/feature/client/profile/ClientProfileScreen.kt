package com.vespelabs.fixit.feature.client.profile

import android.content.res.Configuration.UI_MODE_NIGHT_YES
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
import com.vespelabs.fixit.core.domain.model.ThemeMode
import com.vespelabs.fixit.core.domain.model.User
import com.vespelabs.fixit.core.domain.model.UserRole
import com.vespelabs.fixit.core.ui.components.ErrorContent
import com.vespelabs.fixit.core.ui.components.LoadingContent
import com.vespelabs.fixit.core.ui.components.ScreenScaffold
import com.vespelabs.fixit.core.ui.components.SectionCard
import com.vespelabs.fixit.core.ui.components.ThemeModeRow
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
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    ClientProfileScreen(
        uiState = uiState,
        themeMode = themeMode,
        onEditClick = onEditClick,
        onSignOutClick = viewModel::onSignOutClick,
        onThemeModeChange = viewModel::onThemeModeChange,
        modifier = modifier,
    )
}

@Composable
fun ClientProfileScreen(
    uiState: ClientProfileUiState,
    themeMode: ThemeMode,
    onEditClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
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
                themeMode = themeMode,
                onEditClick = onEditClick,
                onSignOutClick = onSignOutClick,
                onThemeModeChange = onThemeModeChange,
                modifier = contentModifier,
            )
        }
    }
}

@Composable
private fun ProfileDetails(
    user: User,
    themeMode: ThemeMode,
    onEditClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SectionCard(
            title = stringResource(UiR.string.settings_section_account),
            subtitle = stringResource(UiR.string.settings_section_account_hint),
        ) {
            ProfileField(label = stringResource(UiR.string.profile_field_name), value = user.name)
            ProfileField(label = stringResource(R.string.client_profile_email), value = user.email)
            ProfileField(label = stringResource(UiR.string.profile_field_phone), value = user.phone)
            ProfileField(label = stringResource(R.string.client_profile_role), value = user.role.label())
        }
        Button(onClick = onEditClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.client_profile_edit))
        }
        SectionCard(
            title = stringResource(UiR.string.settings_section_appearance),
            subtitle = stringResource(UiR.string.settings_section_appearance_hint),
        ) {
            ThemeModeRow(mode = themeMode, onModeChange = onThemeModeChange)
        }
        SignOutButton(onSignOutClick)
    }
}

@Composable
internal fun ProfileField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
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

@Preview(showBackground = true,uiMode=UI_MODE_NIGHT_YES)
@Composable
private fun ClientProfileScreenPreview() {
    FixItTheme {
        ClientProfileScreen(
            uiState = ClientProfileUiState.Success(
                User(id = "c1", name = "Ana López", email = "ana@fixit.test", phone = "11 5555-0001", role = UserRole.CLIENT),
            ),
            themeMode = ThemeMode.SYSTEM,
            onEditClick = {},
            onSignOutClick = {},
            onThemeModeChange = {},
        )
    }
}
