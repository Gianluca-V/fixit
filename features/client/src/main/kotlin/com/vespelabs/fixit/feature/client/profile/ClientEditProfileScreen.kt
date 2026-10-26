package com.vespelabs.fixit.feature.client.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.model.UserRole
import com.vespelabs.fixit.core.domain.result.ValidationError
import com.vespelabs.fixit.core.ui.components.ErrorContent
import com.vespelabs.fixit.core.ui.components.LoadingContent
import com.vespelabs.fixit.core.ui.components.ProfileFormFields
import com.vespelabs.fixit.core.ui.components.ProfileFormState
import com.vespelabs.fixit.core.ui.map.MapPickerDialog
import com.vespelabs.fixit.core.ui.permission.LocationPermissionGate
import com.vespelabs.fixit.core.ui.theme.FixItTheme
import com.vespelabs.fixit.feature.client.R
import com.vespelabs.fixit.feature.client.common.MessageScaffold
import com.vespelabs.fixit.core.ui.R as UiR

/**
 * "Editar perfil". `:app` resolves onSaved to goBackFrom(ClientEditProfileKey); leaving without
 * saving discards the changes because the ViewModel goes away with the entry.
 */
@Composable
fun ClientEditProfileRoute(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClientEditProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            onSaved()
            viewModel.onNavigated()
        }
    }
    LocationPermissionGate(onGranted = viewModel::onUseLocation) { requestLocation ->
        ClientEditProfileScreen(
            uiState = uiState,
            onBack = onBack,
            onFormChange = viewModel::onFormChange,
            onUseLocation = requestLocation,
            onPlacePicked = viewModel::onPlacePicked,
            onSave = viewModel::onSave,
            onRetry = viewModel::onRetry,
            onMessageShown = viewModel::onMessageShown,
            modifier = modifier,
        )
    }
}

@Composable
fun ClientEditProfileScreen(
    uiState: ClientEditProfileUiState,
    onBack: () -> Unit,
    onFormChange: (ProfileFormState) -> Unit,
    onUseLocation: () -> Unit,
    onPlacePicked: (Place) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MessageScaffold(
        title = stringResource(R.string.client_edit_profile_title),
        message = uiState.message,
        onMessageShown = onMessageShown,
        modifier = modifier,
        onBack = onBack,
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        val loadError = uiState.loadError
        when {
            loadError != null -> ErrorContent(error = loadError, modifier = contentModifier, onRetry = onRetry)
            uiState.isLoading -> LoadingContent(contentModifier)
            else -> EditForm(
                uiState = uiState,
                onFormChange = onFormChange,
                onUseLocation = onUseLocation,
                onPlacePicked = onPlacePicked,
                onSave = onSave,
                modifier = contentModifier,
            )
        }
    }
}

@Composable
private fun EditForm(
    uiState: ClientEditProfileUiState,
    onFormChange: (ProfileFormState) -> Unit,
    onUseLocation: () -> Unit,
    onPlacePicked: (Place) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMapPicker by rememberSaveable { mutableStateOf(false) }
    if (showMapPicker) {
        MapPickerDialog(
            initial = uiState.form.area,
            onConfirm = { place ->
                showMapPicker = false
                onPlacePicked(place)
            },
            onDismiss = { showMapPicker = false },
        )
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Email and role are shown as plain text: there is no way to edit them.
        ProfileField(label = stringResource(R.string.client_profile_email), value = uiState.email)
        ProfileField(label = stringResource(R.string.client_profile_role), value = uiState.role.label())

        ProfileFormFields(
            state = uiState.form,
            onStateChange = onFormChange,
            validationError = uiState.validationError,
            enabled = !uiState.isSaving && !uiState.isLocating,
            onUseLocationClick = onUseLocation,
            onPickOnMapClick = { showMapPicker = true },
            showClientArea = true,
        )
        if (uiState.isLocating) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }

        Button(onClick = onSave, enabled = uiState.canSave, modifier = Modifier.fillMaxWidth()) {
            if (uiState.isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(UiR.string.common_save))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ClientEditProfileScreenPreview() {
    FixItTheme {
        ClientEditProfileScreen(
            uiState = ClientEditProfileUiState(
                isLoading = false,
                email = "ana@fixit.test",
                role = UserRole.CLIENT,
                form = ProfileFormState(name = "Ana López", phone = "12ab"),
                validationError = ValidationError.INVALID_PHONE,
            ),
            onBack = {},
            onFormChange = {},
            onUseLocation = {},
            onPlacePicked = {},
            onSave = {},
            onRetry = {},
            onMessageShown = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ClientEditProfileScreenWithAreaPreview() {
    FixItTheme {
        ClientEditProfileScreen(
            uiState = ClientEditProfileUiState(
                isLoading = false,
                email = "ana@fixit.test",
                role = UserRole.CLIENT,
                form = ProfileFormState(
                    name = "Ana López",
                    phone = "11 5555-0001",
                    area = Place(GeoPoint(-34.7206, -58.2546), "Calle 123, Quilmes"),
                ),
            ),
            onBack = {},
            onFormChange = {},
            onUseLocation = {},
            onPlacePicked = {},
            onSave = {},
            onRetry = {},
            onMessageShown = {},
        )
    }
}
