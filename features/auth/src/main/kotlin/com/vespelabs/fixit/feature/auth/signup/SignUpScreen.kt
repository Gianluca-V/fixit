package com.vespelabs.fixit.feature.auth.signup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vespelabs.fixit.core.domain.model.ServiceCategory
import com.vespelabs.fixit.core.domain.model.UserRole
import com.vespelabs.fixit.core.domain.result.FormField
import com.vespelabs.fixit.core.domain.result.ValidationError
import com.vespelabs.fixit.core.ui.components.ProfileFormFields
import com.vespelabs.fixit.core.ui.components.ProfileFormState
import com.vespelabs.fixit.core.ui.permission.LocationPermissionGate
import com.vespelabs.fixit.core.ui.text.asMessage
import com.vespelabs.fixit.core.ui.theme.FixItTheme
import com.vespelabs.fixit.feature.auth.R
import com.vespelabs.fixit.feature.auth.common.AuthScaffold
import com.vespelabs.fixit.feature.auth.common.CredentialField
import com.vespelabs.fixit.feature.auth.common.SubmitButton

/** Entry point used by `:app`; success needs no callback because the session gate reacts by itself. */
@Composable
fun SignUpRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignUpViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LocationPermissionGate(onGranted = viewModel::onUseLocation) { requestLocation ->
        SignUpScreen(
            uiState = uiState,
            onBack = onBack,
            onEmailChange = viewModel::onEmailChange,
            onPasswordChange = viewModel::onPasswordChange,
            onRoleSelect = viewModel::onRoleSelect,
            onProfileChange = viewModel::onProfileChange,
            onUseLocation = requestLocation,
            onSubmit = viewModel::onSubmit,
            onMessageShown = viewModel::onMessageShown,
            modifier = modifier,
        )
    }
}

@Composable
fun SignUpScreen(
    uiState: SignUpUiState,
    onBack: () -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRoleSelect: (UserRole) -> Unit,
    onProfileChange: (ProfileFormState) -> Unit,
    onUseLocation: () -> Unit,
    onSubmit: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = !uiState.isSubmitting
    fun errorFor(field: FormField) = uiState.validationError?.takeIf { it.field == field }

    AuthScaffold(
        title = stringResource(R.string.auth_sign_up_title),
        message = uiState.message,
        onMessageShown = onMessageShown,
        modifier = modifier,
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CredentialField(
                value = uiState.email,
                onValueChange = onEmailChange,
                label = stringResource(R.string.auth_field_email),
                error = errorFor(FormField.EMAIL),
                enabled = enabled,
                keyboardType = KeyboardType.Email,
            )
            CredentialField(
                value = uiState.password,
                onValueChange = onPasswordChange,
                label = stringResource(R.string.auth_field_password),
                error = errorFor(FormField.PASSWORD),
                enabled = enabled,
                keyboardType = KeyboardType.Password,
            )
            RoleSelector(
                selected = uiState.role,
                onRoleSelect = onRoleSelect,
                error = errorFor(FormField.ROLE),
                enabled = enabled,
            )
            ProfileFormFields(
                state = uiState.profile,
                onStateChange = onProfileChange,
                showProfessionalFields = uiState.showProfessionalFields,
                // Errors of the other fields are already shown above.
                validationError = uiState.validationError?.takeUnless {
                    it.field in setOf(FormField.EMAIL, FormField.PASSWORD, FormField.ROLE)
                },
                enabled = enabled && !uiState.isLocating,
                onUseLocationClick = onUseLocation,
            )
            SubmitButton(
                text = stringResource(R.string.auth_sign_up_submit),
                isSubmitting = uiState.isSubmitting,
                onClick = onSubmit,
            )
        }
    }
}

/** The role is chosen only here: no screen offers a way to change it after signing up. */
@Composable
private fun RoleSelector(
    selected: UserRole?,
    onRoleSelect: (UserRole) -> Unit,
    error: ValidationError?,
    enabled: Boolean,
) {
    Column(Modifier.selectableGroup()) {
        Text(stringResource(R.string.auth_role_label), style = MaterialTheme.typography.labelLarge)
        Text(stringResource(R.string.auth_role_fixed_hint), style = MaterialTheme.typography.bodySmall)
        listOf(
            UserRole.CLIENT to R.string.auth_role_client,
            UserRole.PROFESSIONAL to R.string.auth_role_professional,
        ).forEach { (role, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = role == selected,
                        onClick = { onRoleSelect(role) },
                        enabled = enabled,
                        role = Role.RadioButton,
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = role == selected, onClick = null, enabled = enabled)
                Text(stringResource(label), modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (error != null) {
            Text(error.asMessage(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SignUpScreenPreview() {
    FixItTheme {
        SignUpScreen(
            uiState = SignUpUiState(
                email = "ana@fixit.test",
                role = UserRole.CLIENT,
                profile = ProfileFormState(name = "Ana López", phone = "12ab"),
                validationError = ValidationError.INVALID_PHONE,
            ),
            onBack = {},
            onEmailChange = {},
            onPasswordChange = {},
            onRoleSelect = {},
            onProfileChange = {},
            onUseLocation = {},
            onSubmit = {},
            onMessageShown = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SignUpScreenProfessionalPreview() {
    FixItTheme {
        SignUpScreen(
            uiState = SignUpUiState(
                email = "diana@fixit.test",
                role = UserRole.PROFESSIONAL,
                profile = ProfileFormState(name = "Diana Ruiz", phone = "11 5555-0102", categories = setOf(ServiceCategory.GAS)),
                isSubmitting = true,
            ),
            onBack = {},
            onEmailChange = {},
            onPasswordChange = {},
            onRoleSelect = {},
            onProfileChange = {},
            onUseLocation = {},
            onSubmit = {},
            onMessageShown = {},
        )
    }
}
