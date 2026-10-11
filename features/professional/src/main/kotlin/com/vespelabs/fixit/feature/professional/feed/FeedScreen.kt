package com.vespelabs.fixit.feature.professional.feed

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.vespelabs.fixit.core.ui.components.PlaceholderScreen
import com.vespelabs.fixit.core.ui.theme.FixItTheme
import com.vespelabs.fixit.feature.professional.R

/**
 * Root of the "Feed" tab. The feature never navigates: `:app` passes the callbacks.
 * TODO(task 1.9.1/1.9.2): add FeedViewModel (tests first), `hiltViewModel()` here, and the real feed.
 */
@Composable
fun FeedRoute(
    onRequestClick: (requestId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FeedScreen(onRequestClick = onRequestClick, modifier = modifier)
}

@Composable
fun FeedScreen(
    onRequestClick: (requestId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(title = stringResource(R.string.pro_feed_title), taskRef = "1.9.2", modifier = modifier) {
        Button(onClick = { onRequestClick("r1") }) { Text(stringResource(R.string.pro_placeholder_open_request)) } // seed request in Carlos's feed
    }
}

@Preview(showBackground = true)
@Composable
private fun FeedScreenPreview() {
    FixItTheme { FeedScreen(onRequestClick = {}) }
}
