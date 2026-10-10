package ar.edu.unaj.fixit.feature.client.complete

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
 * "Completar y calificar" for [requestId]. `:app` resolves onCompleted to goBackFrom(CompleteJobKey).
 * TODO(task 1.8.7/1.8.8): add CompleteJobViewModel (tests first; loads the detail with
 * ObserveRequestDetailUseCase) and the real rating form.
 */
@Composable
fun CompleteJobRoute(
    requestId: String,
    onBack: () -> Unit,
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CompleteJobScreen(requestId = requestId, onBack = onBack, onCompleted = onCompleted, modifier = modifier)
}

@Composable
fun CompleteJobScreen(
    requestId: String,
    onBack: () -> Unit,
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        title = stringResource(R.string.client_complete_job_title),
        taskRef = "1.8.8",
        modifier = modifier,
        onBack = onBack,
    ) {
        Text(requestId)
        Button(onClick = onCompleted) { Text(stringResource(R.string.client_placeholder_completed)) }
    }
}

@Preview(showBackground = true)
@Composable
private fun CompleteJobScreenPreview() {
    FixItTheme { CompleteJobScreen(requestId = "r1", onBack = {}, onCompleted = {}) }
}
