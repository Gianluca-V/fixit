package ar.edu.unaj.fixit.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.edu.unaj.fixit.core.ui.R
import ar.edu.unaj.fixit.core.ui.theme.FixItTheme

/**
 * TEMPORARY body for screens that are not implemented yet (app shell before features 1.7-1.9).
 * [taskRef] is the tasks.md id that replaces it, e.g. "1.8.2". Delete this file when no screen uses it.
 */
@Composable
fun PlaceholderScreen(
    title: String,
    taskRef: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    ScreenScaffold(title = title, modifier = modifier, onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.placeholder_message, taskRef), style = MaterialTheme.typography.bodyLarge)
            content()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaceholderScreenPreview() {
    FixItTheme {
        PlaceholderScreen(title = "Mis solicitudes", taskRef = "1.8.2") {
            Button(onClick = {}) { Text("Abrir solicitud de ejemplo") }
        }
    }
}
