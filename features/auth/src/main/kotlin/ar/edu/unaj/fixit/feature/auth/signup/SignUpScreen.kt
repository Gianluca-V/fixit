package ar.edu.unaj.fixit.feature.auth.signup

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import ar.edu.unaj.fixit.core.ui.components.PlaceholderScreen
import ar.edu.unaj.fixit.core.ui.theme.FixItTheme
import ar.edu.unaj.fixit.feature.auth.R

/**
 * Entry point used by `:app`; success needs no callback because the session gate reacts by itself.
 * TODO(task 1.7.3/1.7.4): add SignUpViewModel (tests first) and replace this placeholder with the real form.
 */
@Composable
fun SignUpRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SignUpScreen(onBack = onBack, modifier = modifier)
}

@Composable
fun SignUpScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        title = stringResource(R.string.auth_sign_up_title),
        taskRef = "1.7.4",
        modifier = modifier,
        onBack = onBack,
    )
}

@Preview(showBackground = true)
@Composable
private fun SignUpScreenPreview() {
    FixItTheme { SignUpScreen(onBack = {}) }
}
