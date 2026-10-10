package ar.edu.unaj.fixit.feature.client.profile

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.unaj.fixit.core.domain.model.UserRole
import ar.edu.unaj.fixit.core.domain.result.ValidationError
import ar.edu.unaj.fixit.core.ui.components.ErrorContent
import ar.edu.unaj.fixit.core.ui.components.LoadingContent
import ar.edu.unaj.fixit.core.ui.components.ProfileFormFields
import ar.edu.unaj.fixit.core.ui.components.ProfileFormState
import ar.edu.unaj.fixit.core.ui.theme.FixItTheme
import ar.edu.unaj.fixit.feature.client.R
import ar.edu.unaj.fixit.feature.client.common.MessageScaffold
import ar.edu.unaj.fixit.core.ui.R as UiR

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
    ClientEditProfileScreen(
        uiState = uiState,
        onBack = onBack,
        onFormChange = viewModel::onFormChange,
        onSave = viewModel::onSave,
        onRetry = viewModel::onRetry,
        onMessageShown = viewModel::onMessageShown,
        modifier = modifier,
    )
}

@Composable
fun ClientEditProfileScreen(
    uiState: ClientEditProfileUiState,
    onBack: () -> Unit,
    onFormChange: (ProfileFormState) -> Unit,
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
            else -> EditForm(uiState = uiState, onFormChange = onFormChange, onSave = onSave, modifier = contentModifier)
        }
    }
}

@Composable
private fun EditForm(
    uiState: ClientEditProfileUiState,
    onFormChange: (ProfileFormState) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            enabled = !uiState.isSaving,
        )

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
            onSave = {},
            onRetry = {},
            onMessageShown = {},
        )
    }
}
