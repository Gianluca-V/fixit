package ar.edu.unaj.fixit.feature.client.detail

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
 * Detail of one of the client's requests; [requestId] comes from the navigation key.
 * TODO(task 1.8.5/1.8.6): add ClientRequestDetailViewModel (tests first) with an idempotent
 * `load(requestId)` called from `LaunchedEffect(requestId)`, and the real detail (design 8).
 */
@Composable
fun ClientRequestDetailRoute(
    requestId: String,
    onBack: () -> Unit,
    onCompleteClick: (requestId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ClientRequestDetailScreen(requestId = requestId, onBack = onBack, onCompleteClick = onCompleteClick, modifier = modifier)
}

@Composable
fun ClientRequestDetailScreen(
    requestId: String,
    onBack: () -> Unit,
    onCompleteClick: (requestId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        title = stringResource(R.string.client_request_detail_title),
        taskRef = "1.8.6",
        modifier = modifier,
        onBack = onBack,
    ) {
        Text(requestId)
        Button(onClick = { onCompleteClick(requestId) }) { Text(stringResource(R.string.client_placeholder_complete)) }
    }
}

@Preview(showBackground = true)
@Composable
private fun ClientRequestDetailScreenPreview() {
    FixItTheme { ClientRequestDetailScreen(requestId = "r1", onBack = {}, onCompleteClick = {}) }
}
