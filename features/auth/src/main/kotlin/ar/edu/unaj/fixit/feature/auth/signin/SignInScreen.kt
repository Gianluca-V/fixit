package ar.edu.unaj.fixit.feature.auth.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.unaj.fixit.core.domain.result.FormField
import ar.edu.unaj.fixit.core.domain.result.ValidationError
import ar.edu.unaj.fixit.core.ui.theme.FixItTheme
import ar.edu.unaj.fixit.feature.auth.R
import ar.edu.unaj.fixit.feature.auth.common.AuthScaffold
import ar.edu.unaj.fixit.feature.auth.common.CredentialField
import ar.edu.unaj.fixit.feature.auth.common.SubmitButton

/** Entry point used by `:app`. The feature never navigates: `:app` passes the callbacks. */
@Composable
fun SignInRoute(
    onSignUpClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SignInScreen(
        uiState = uiState,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::onSubmit,
        onSignUpClick = onSignUpClick,
        onMessageShown = viewModel::onMessageShown,
        modifier = modifier,
    )
}

@Composable
fun SignInScreen(
    uiState: SignInUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSignUpClick: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = !uiState.isSubmitting
    AuthScaffold(
        title = stringResource(R.string.auth_sign_in_title),
        message = uiState.message,
        onMessageShown = onMessageShown,
        modifier = modifier,
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
                error = uiState.validationError?.takeIf { it.field == FormField.EMAIL },
                enabled = enabled,
                keyboardType = KeyboardType.Email,
            )
            CredentialField(
                value = uiState.password,
                onValueChange = onPasswordChange,
                label = stringResource(R.string.auth_field_password),
                error = uiState.validationError?.takeIf { it.field == FormField.PASSWORD },
                enabled = enabled,
                keyboardType = KeyboardType.Password,
            )
            SubmitButton(
                text = stringResource(R.string.auth_sign_in_submit),
                isSubmitting = uiState.isSubmitting,
                onClick = onSubmit,
            )
            TextButton(onClick = onSignUpClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.auth_go_to_sign_up))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SignInScreenPreview() {
    FixItTheme {
        SignInScreen(
            uiState = SignInUiState(email = "ana@", validationError = ValidationError.INVALID_EMAIL),
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {},
            onSignUpClick = {},
            onMessageShown = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SignInScreenSubmittingPreview() {
    FixItTheme {
        SignInScreen(
            uiState = SignInUiState(email = "ana@fixit.test", password = "test1234", isSubmitting = true),
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {},
            onSignUpClick = {},
            onMessageShown = {},
        )
    }
}
