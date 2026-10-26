package com.vespelabs.fixit.feature.professional.profile

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
import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.model.ProfessionalInfo
import com.vespelabs.fixit.core.domain.model.Reputation
import com.vespelabs.fixit.core.domain.model.ServiceCategory
import com.vespelabs.fixit.core.domain.model.ThemeMode
import com.vespelabs.fixit.core.domain.model.User
import com.vespelabs.fixit.core.domain.model.UserRole
import com.vespelabs.fixit.core.ui.components.ErrorContent
import com.vespelabs.fixit.core.ui.components.LoadingContent
import com.vespelabs.fixit.core.ui.components.ReputationText
import com.vespelabs.fixit.core.ui.components.ScreenScaffold
import com.vespelabs.fixit.core.ui.components.SectionCard
import com.vespelabs.fixit.core.ui.components.ThemeModeRow
import com.vespelabs.fixit.core.ui.text.label
import com.vespelabs.fixit.core.ui.theme.FixItTheme
import com.vespelabs.fixit.feature.professional.R
import com.vespelabs.fixit.feature.professional.common.isRetryable
import java.util.Locale
import com.vespelabs.fixit.core.ui.R as UiR

/** Root of the "Perfil" tab. Sign out lives here (the session gate in `:app` reacts by itself). */
@Composable
fun ProProfileRoute(
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    ProProfileScreen(
        uiState = uiState,
        themeMode = themeMode,
        onEditClick = onEditClick,
        onSignOutClick = viewModel::onSignOutClick,
        onThemeModeChange = viewModel::onThemeModeChange,
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )
}

@Composable
fun ProProfileScreen(
    uiState: ProProfileUiState,
    themeMode: ThemeMode,
    onEditClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(title = stringResource(R.string.pro_profile_title), modifier = modifier) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (uiState) {
            ProProfileUiState.Loading -> LoadingContent(contentModifier)
            // Sign out stays reachable even when the profile cannot be read.
            is ProProfileUiState.Error -> Column(contentModifier.fillMaxSize()) {
                ErrorContent(
                    error = uiState.error,
                    modifier = Modifier.weight(1f),
                    onRetry = onRetry.takeIf { uiState.error.isRetryable() },
                )
                SignOutButton(onSignOutClick, Modifier.padding(16.dp))
            }
            is ProProfileUiState.Success -> ProfileDetails(
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
    val professional = user.professional
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
            ProfileField(label = stringResource(R.string.pro_profile_email), value = user.email)
            ProfileField(label = stringResource(UiR.string.profile_field_phone), value = user.phone)
        }
        if (professional != null) {
            SectionCard(
                title = stringResource(R.string.pro_profile_section_services),
                subtitle = stringResource(R.string.pro_profile_section_services_hint),
            ) {
                ProfileField(
                    label = stringResource(UiR.string.profile_field_categories),
                    value = professional.categories.sortedBy { it.ordinal }.map { it.label() }.joinToString(", "),
                )
                ProfileField(
                    label = stringResource(R.string.pro_profile_radius),
                    value = stringResource(R.string.pro_profile_radius_value, professional.radiusKm),
                )
                ProfileField(label = stringResource(UiR.string.profile_field_base), value = professional.base.display())
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.pro_profile_reputation),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ReputationText(professional.reputation)
                }
            }
        }
        Button(onClick = onEditClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.pro_profile_edit))
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

/** The address when there is one; otherwise the coordinates (spec: "dirección o coordenadas"). */
private fun Place.display(): String =
    address.ifBlank { "%.5f, %.5f".format(Locale.ROOT, point.latitude, point.longitude) }

@Composable
private fun ProfileField(label: String, value: String, modifier: Modifier = Modifier) {
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

@Preview(showBackground = true)
@Composable
private fun ProProfileScreenPreview() {
    FixItTheme {
        ProProfileScreen(
            uiState = ProProfileUiState.Success(
                User(
                    id = "p1",
                    name = "Carlos Gómez",
                    email = "carlos@fixit.test",
                    phone = "11 5555-0101",
                    role = UserRole.PROFESSIONAL,
                    professional = ProfessionalInfo(
                        categories = setOf(ServiceCategory.ELECTRICITY),
                        base = Place(GeoPoint(-34.8065, -58.2788), "Av. San Martín 2000, Florencio Varela"),
                        radiusKm = 15,
                        reputation = Reputation(ratingSum = 13, reviewCount = 3),
                    ),
                ),
            ),
            themeMode = ThemeMode.DARK,
            onEditClick = {},
            onSignOutClick = {},
            onThemeModeChange = {},
            onRetry = {},
        )
    }
}
