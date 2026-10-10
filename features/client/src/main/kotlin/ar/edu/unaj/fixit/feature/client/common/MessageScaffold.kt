package ar.edu.unaj.fixit.feature.client.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ar.edu.unaj.fixit.core.domain.result.DomainError
import ar.edu.unaj.fixit.core.domain.result.ValidationError
import ar.edu.unaj.fixit.core.ui.components.ScreenScaffold
import ar.edu.unaj.fixit.core.ui.text.asMessage

/**
 * [ScreenScaffold] plus a snackbar for the one-shot [message]: it is shown once and then cleared
 * with [onMessageShown], so it does not come back after a rotation.
 */
@Composable
internal fun MessageScaffold(
    title: String,
    message: DomainError?,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val text = message?.asMessage()
    LaunchedEffect(text) {
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            onMessageShown()
        }
    }
    ScreenScaffold(title = title, modifier = modifier, onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize()) {
            content(padding)
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(padding),
            )
        }
    }
}

/** Validation message shown under a form section; nothing when [error] is null. */
@Composable
internal fun FieldErrorText(error: ValidationError?, modifier: Modifier = Modifier) {
    if (error != null) {
        Text(
            text = error.asMessage(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = modifier,
        )
    }
}
