package ar.edu.unaj.fixit.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.edu.unaj.fixit.core.domain.result.DomainError
import ar.edu.unaj.fixit.core.ui.R
import ar.edu.unaj.fixit.core.ui.text.asMessage
import ar.edu.unaj.fixit.core.ui.theme.FixItTheme

/** Full-size error message with an optional retry button. */
@Composable
fun ErrorContent(
    error: DomainError,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = error.asMessage(),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Button(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ErrorContentPreview() {
    FixItTheme { ErrorContent(error = DomainError.Network, onRetry = {}) }
}
