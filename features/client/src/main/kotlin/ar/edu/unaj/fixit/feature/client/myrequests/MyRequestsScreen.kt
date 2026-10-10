package ar.edu.unaj.fixit.feature.client.myrequests

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
 * Root of the "Solicitudes" tab. The feature never navigates: `:app` passes the callbacks.
 * TODO(task 1.8.1/1.8.2): add MyRequestsViewModel (tests first), `hiltViewModel()` here, and the real list.
 */
@Composable
fun MyRequestsRoute(
    onCreateClick: () -> Unit,
    onRequestClick: (requestId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    MyRequestsScreen(onCreateClick = onCreateClick, onRequestClick = onRequestClick, modifier = modifier)
}

@Composable
fun MyRequestsScreen(
    onCreateClick: () -> Unit,
    onRequestClick: (requestId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(title = stringResource(R.string.client_my_requests_title), taskRef = "1.8.2", modifier = modifier) {
        Button(onClick = onCreateClick) { Text(stringResource(R.string.client_placeholder_new_request)) }
        Button(onClick = { onRequestClick("r1") }) { Text(stringResource(R.string.client_placeholder_open_request)) } // seed request of Ana
    }
}

@Preview(showBackground = true)
@Composable
private fun MyRequestsScreenPreview() {
    FixItTheme { MyRequestsScreen(onCreateClick = {}, onRequestClick = {}) }
}
