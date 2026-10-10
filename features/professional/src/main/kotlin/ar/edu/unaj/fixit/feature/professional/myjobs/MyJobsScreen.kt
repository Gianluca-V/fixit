package ar.edu.unaj.fixit.feature.professional.myjobs

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
 * Root of the "Mis trabajos" tab. The feature never navigates: `:app` passes the callbacks.
 * TODO(task 1.9.5/1.9.6): add MyJobsViewModel (tests first; active vs finished) and the real list.
 */
@Composable
fun MyJobsRoute(
    onJobClick: (requestId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    MyJobsScreen(onJobClick = onJobClick, modifier = modifier)
}

@Composable
fun MyJobsScreen(
    onJobClick: (requestId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(title = stringResource(R.string.pro_my_jobs_title), taskRef = "1.9.6", modifier = modifier) {
        Button(onClick = { onJobClick("r5") }) { Text(stringResource(R.string.pro_placeholder_open_request)) } // seed job of Carlos
    }
}

@Preview(showBackground = true)
@Composable
private fun MyJobsScreenPreview() {
    FixItTheme { MyJobsScreen(onJobClick = {}) }
}
