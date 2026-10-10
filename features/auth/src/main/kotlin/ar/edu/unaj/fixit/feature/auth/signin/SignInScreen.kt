package ar.edu.unaj.fixit.feature.auth.signin

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.unaj.fixit.core.ui.components.PlaceholderScreen
import ar.edu.unaj.fixit.core.ui.text.asMessage
import ar.edu.unaj.fixit.core.ui.theme.FixItTheme
import ar.edu.unaj.fixit.feature.auth.R

/** Seed accounts (design 12). Password is the same for all of them; local data only. */
private const val SEED_PASSWORD = "test1234"

/**
 * Entry point used by `:app`. The feature never navigates: `:app` passes the callbacks.
 * TODO(task 1.7.2): replace the placeholder body with the real sign-in form.
 */
@Composable
fun SignInRoute(
    onSignUpClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SignInScreen(
        uiState = uiState,
        onSignIn = viewModel::onSignIn,
        onSignUpClick = onSignUpClick,
        modifier = modifier,
    )
}

@Composable
fun SignInScreen(
    uiState: SignInUiState,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUpClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // TODO(task 1.7.2): TEMPORARY quick sign-in with the seed users.
    PlaceholderScreen(title = stringResource(R.string.auth_sign_in_title), taskRef = "1.7.2", modifier = modifier) {
        listOf(
            R.string.auth_demo_sign_in_ana to "ana@fixit.test",
            R.string.auth_demo_sign_in_bruno to "bruno@fixit.test",
            R.string.auth_demo_sign_in_carlos to "carlos@fixit.test",
            R.string.auth_demo_sign_in_diana to "diana@fixit.test",
        ).forEach { (label, email) ->
            Button(onClick = { onSignIn(email, SEED_PASSWORD) }, enabled = !uiState.isSubmitting) {
                Text(stringResource(label))
            }
        }
        OutlinedButton(onClick = onSignUpClick, enabled = !uiState.isSubmitting) {
            Text(stringResource(R.string.auth_go_to_sign_up))
        }
        uiState.message?.let {
            Text(it.asMessage(), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SignInScreenPreview() {
    FixItTheme { SignInScreen(uiState = SignInUiState(), onSignIn = { _, _ -> }, onSignUpClick = {}) }
}
