package ar.edu.unaj.fixit.feature.client.create

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import ar.edu.unaj.fixit.core.ui.components.PlaceholderScreen
import ar.edu.unaj.fixit.core.ui.theme.FixItTheme
import ar.edu.unaj.fixit.feature.client.R

/**
 * "Nueva solicitud". `:app` resolves onCreated(id) to goBackFrom(CreateRequestKey) + navigate(detail).
 * TODO(task 1.8.3/1.8.4): add CreateRequestViewModel (tests first), `hiltViewModel()` here, and the real form.
 */
@Composable
fun CreateRequestRoute(
    onBack: () -> Unit,
    onCreated: (requestId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    CreateRequestScreen(onBack = onBack, onCreated = onCreated, modifier = modifier)
}

@Composable
fun CreateRequestScreen(
    onBack: () -> Unit,
    onCreated: (requestId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        title = stringResource(R.string.client_create_request_title),
        taskRef = "1.8.4",
        modifier = modifier,
        onBack = onBack,
    ) {
        Button(onClick = { onCreated("r1") }) { Text(stringResource(R.string.client_placeholder_created)) }
    }
}

@Preview(showBackground = true)
@Composable
private fun CreateRequestScreenPreview() {
    FixItTheme { CreateRequestScreen(onBack = {}, onCreated = {}) }
}
