package com.vespelabs.fixit.feature.client.complete

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vespelabs.fixit.core.domain.model.Limits
import com.vespelabs.fixit.core.domain.result.FormField
import com.vespelabs.fixit.core.ui.components.ErrorContent
import com.vespelabs.fixit.core.ui.components.LoadingContent
import com.vespelabs.fixit.core.ui.components.RatingBar
import com.vespelabs.fixit.core.ui.text.asMessage
import com.vespelabs.fixit.core.ui.theme.FixItTheme
import com.vespelabs.fixit.feature.client.R
import com.vespelabs.fixit.feature.client.common.FieldErrorText
import com.vespelabs.fixit.feature.client.common.MessageScaffold

/** "Completar y calificar" for [requestId]. `:app` resolves onCompleted to goBackFrom(CompleteJobKey). */
@Composable
fun CompleteJobRoute(
    requestId: String,
    onBack: () -> Unit,
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CompleteJobViewModel = hiltViewModel(),
) {
    LaunchedEffect(requestId) { viewModel.load(requestId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.completed) {
        if (uiState.completed) {
            onCompleted()
            viewModel.onNavigated()
        }
    }
    CompleteJobScreen(
        uiState = uiState,
        onBack = onBack,
        onRatingChange = viewModel::onRatingChange,
        onCommentChange = viewModel::onCommentChange,
        onSubmit = viewModel::onSubmit,
        onRetry = viewModel::onRetry,
        onMessageShown = viewModel::onMessageShown,
        modifier = modifier,
    )
}

@Composable
fun CompleteJobScreen(
    uiState: CompleteJobUiState,
    onBack: () -> Unit,
    onRatingChange: (Int) -> Unit,
    onCommentChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MessageScaffold(
        title = stringResource(R.string.client_complete_job_title),
        message = uiState.message,
        onMessageShown = onMessageShown,
        modifier = modifier,
        onBack = onBack,
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        val loadError = uiState.loadError
        when {
            loadError != null -> ErrorContent(error = loadError, modifier = contentModifier, onRetry = onRetry)
            uiState.isLoading -> LoadingContent(contentModifier)
            else -> RatingForm(
                uiState = uiState,
                onRatingChange = onRatingChange,
                onCommentChange = onCommentChange,
                onSubmit = onSubmit,
                modifier = contentModifier,
            )
        }
    }
}

@Composable
private fun RatingForm(
    uiState: CompleteJobUiState,
    onRatingChange: (Int) -> Unit,
    onCommentChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = !uiState.isSubmitting
    val commentError = uiState.validationError?.takeIf { it.field == FormField.COMMENT }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        uiState.professionalName?.let {
            Text(stringResource(R.string.client_complete_professional, it), style = MaterialTheme.typography.titleMedium)
        }
        Text(stringResource(R.string.client_complete_rating), style = MaterialTheme.typography.labelLarge)
        RatingBar(rating = uiState.rating, onRatingChange = if (enabled) onRatingChange else null)
        FieldErrorText(uiState.validationError?.takeIf { it.field == FormField.RATING })

        OutlinedTextField(
            value = uiState.comment,
            onValueChange = onCommentChange,
            label = { Text(stringResource(R.string.client_complete_comment)) },
            isError = commentError != null,
            supportingText = {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(commentError?.asMessage().orEmpty(), modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.client_complete_comment_counter, uiState.comment.length, Limits.COMMENT_MAX))
                }
            },
            enabled = enabled,
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(onClick = onSubmit, enabled = uiState.canSubmit, modifier = Modifier.fillMaxWidth()) {
            if (uiState.isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.client_complete_submit))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CompleteJobScreenPreview() {
    FixItTheme {
        CompleteJobScreen(
            uiState = CompleteJobUiState(isLoading = false, professionalName = "Carlos Gómez", rating = 4, comment = "Muy prolijo"),
            onBack = {},
            onRatingChange = {},
            onCommentChange = {},
            onSubmit = {},
            onRetry = {},
            onMessageShown = {},
        )
    }
}
