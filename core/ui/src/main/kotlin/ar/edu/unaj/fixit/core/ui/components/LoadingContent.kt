package ar.edu.unaj.fixit.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import ar.edu.unaj.fixit.core.ui.R
import ar.edu.unaj.fixit.core.ui.theme.FixItTheme

/** Full-size centered progress indicator for a screen that is loading. */
@Composable
fun LoadingContent(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.common_loading)
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadingContentPreview() {
    FixItTheme { LoadingContent() }
}
