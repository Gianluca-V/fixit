package ar.edu.unaj.fixit.feature.professional.detail

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import ar.edu.unaj.fixit.core.ui.components.PlaceholderScreen
import ar.edu.unaj.fixit.core.ui.theme.FixItTheme
import ar.edu.unaj.fixit.feature.professional.R

/**
 * Detail of a request for a professional; [requestId] comes from the navigation key.
 * `:app` resolves onAccepted(id) to goBackFrom + selectTab(MY_JOBS) + resetToRoot + navigate(detail);
 * when the race is lost the screen shows its dialog and, on confirm, calls [onBack].
 * TODO(task 1.9.3/1.9.4): add ProRequestDetailViewModel (tests first) with an idempotent
 * `load(requestId)` called from `LaunchedEffect(requestId)`, and the real detail (design 9).
 */
@Composable
fun ProRequestDetailRoute(
    requestId: String,
    onBack: () -> Unit,
    onAccepted: (requestId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ProRequestDetailScreen(requestId = requestId, onBack = onBack, onAccepted = onAccepted, modifier = modifier)
}

@Composable
fun ProRequestDetailScreen(
    requestId: String,
    onBack: () -> Unit,
    onAccepted: (requestId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        title = stringResource(R.string.pro_request_detail_title),
        taskRef = "1.9.4",
        modifier = modifier,
        onBack = onBack,
    ) {
        Text(requestId)
        Button(onClick = { onAccepted(requestId) }) { Text(stringResource(R.string.pro_placeholder_accept)) }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProRequestDetailScreenPreview() {
    FixItTheme { ProRequestDetailScreen(requestId = "r1", onBack = {}, onAccepted = {}) }
}
