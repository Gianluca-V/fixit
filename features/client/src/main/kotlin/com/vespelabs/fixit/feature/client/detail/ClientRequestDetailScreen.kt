package com.vespelabs.fixit.feature.client.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vespelabs.fixit.core.domain.model.Contact
import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.model.RepairRequest
import com.vespelabs.fixit.core.domain.model.RequestDetail
import com.vespelabs.fixit.core.domain.model.RequestStatus
import com.vespelabs.fixit.core.domain.model.Reputation
import com.vespelabs.fixit.core.domain.model.Review
import com.vespelabs.fixit.core.domain.model.ServiceCategory
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.ui.components.ContactCard
import com.vespelabs.fixit.core.ui.components.ErrorContent
import com.vespelabs.fixit.core.ui.components.LoadingContent
import com.vespelabs.fixit.core.ui.components.PhotoPager
import com.vespelabs.fixit.core.ui.components.RatingBar
import com.vespelabs.fixit.core.ui.components.SamplePhotos
import com.vespelabs.fixit.core.ui.components.StatusBadge
import com.vespelabs.fixit.core.ui.text.label
import com.vespelabs.fixit.core.ui.theme.FixItTheme
import com.vespelabs.fixit.feature.client.R
import com.vespelabs.fixit.feature.client.common.MessageScaffold
import java.text.DateFormat
import java.util.Date

/**
 * Detail of one of the client's requests; [requestId] comes from the navigation key and is passed to
 * the idempotent `load` (D4). `:app` resolves onCompleteClick(id) to navigate(CompleteJobKey(id)).
 */
@Composable
fun ClientRequestDetailRoute(
    requestId: String,
    onBack: () -> Unit,
    onCompleteClick: (requestId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClientRequestDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(requestId) { viewModel.load(requestId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ClientRequestDetailScreen(
        uiState = uiState,
        onBack = onBack,
        onCompleteClick = { onCompleteClick(requestId) },
        onCancelConfirm = viewModel::onCancelConfirm,
        onRetry = viewModel::onRetry,
        onMessageShown = viewModel::onMessageShown,
        modifier = modifier,
    )
}

@Composable
fun ClientRequestDetailScreen(
    uiState: ClientRequestDetailUiState,
    onBack: () -> Unit,
    onCompleteClick: () -> Unit,
    onCancelConfirm: () -> Unit,
    onRetry: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MessageScaffold(
        title = stringResource(R.string.client_request_detail_title),
        message = (uiState as? ClientRequestDetailUiState.Success)?.message,
        onMessageShown = onMessageShown,
        modifier = modifier,
        onBack = onBack,
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (uiState) {
            ClientRequestDetailUiState.Loading -> LoadingContent(contentModifier)
            // NotFound / Unauthorized: the top bar offers going back; retrying only helps other errors.
            is ClientRequestDetailUiState.Error -> ErrorContent(
                error = uiState.error,
                modifier = contentModifier,
                onRetry = onRetry.takeIf { uiState.error.isRetryable() },
            )
            is ClientRequestDetailUiState.Success -> DetailContent(
                state = uiState,
                onCompleteClick = onCompleteClick,
                onCancelConfirm = onCancelConfirm,
                modifier = contentModifier,
            )
        }
    }
}

private fun DomainError.isRetryable(): Boolean = this != DomainError.NotFound && this != DomainError.Unauthorized

@Composable
private fun DetailContent(
    state: ClientRequestDetailUiState.Success,
    onCompleteClick: () -> Unit,
    onCancelConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val request = state.detail.request
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    var confirmingCancel by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(request.category.label(), style = MaterialTheme.typography.titleLarge)
            StatusBadge(request.status)
        }
        Text(request.description, style = MaterialTheme.typography.bodyLarge)
        PhotoPager(photoUrls = request.photoUrls)
        Text(request.place.address, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = stringResource(R.string.client_request_created_on, dateFormat.format(Date(request.createdAt))),
            style = MaterialTheme.typography.bodySmall,
        )

        StatusContent(state.detail)

        if (state.canComplete) {
            Button(onClick = onCompleteClick, enabled = !state.isWorking, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.client_detail_complete))
            }
        }
        if (state.canCancel) {
            OutlinedButton(
                onClick = { confirmingCancel = true },
                enabled = !state.isWorking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isWorking) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.client_detail_cancel))
                }
            }
        }
    }

    if (confirmingCancel) {
        CancelDialog(
            onConfirm = {
                confirmingCancel = false
                onCancelConfirm()
            },
            onDismiss = { confirmingCancel = false },
        )
    }
}

/** The extra content of each status (spec table): waiting text, contact, review or cancelled text. */
@Composable
private fun StatusContent(detail: RequestDetail) {
    when (detail.request.status) {
        RequestStatus.OPEN -> InfoText(stringResource(R.string.client_detail_waiting))
        RequestStatus.CANCELLED -> InfoText(stringResource(R.string.client_detail_cancelled))
        RequestStatus.ACCEPTED, RequestStatus.COMPLETED -> {
            // The contact carries the assigned professional's name and reputation (ContactCard).
            detail.contact?.let { ContactCard(it, Modifier.fillMaxWidth()) }
            detail.review?.let { ReviewCard(it) }
        }
    }
}

@Composable
private fun InfoText(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
}

@Composable
private fun ReviewCard(review: Review) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.client_detail_review), style = MaterialTheme.typography.labelLarge)
            RatingBar(rating = review.rating)
            review.comment?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun CancelDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.client_detail_cancel_title)) },
        text = { Text(stringResource(R.string.client_detail_cancel_text)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.client_detail_cancel_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.client_detail_cancel_dismiss)) } },
    )
}

private fun previewDetail(status: RequestStatus, withContact: Boolean, review: Review? = null) = RequestDetail(
    request = RepairRequest(
        id = "r1",
        clientId = "c1",
        category = ServiceCategory.PLUMBING,
        description = "La canilla de la cocina pierde agua desde hace dos días y no cierra del todo.",
        photoUrls = SamplePhotos.take(2),
        place = Place(GeoPoint(-34.8065, -58.2788), "Av. San Martín 2000, Florencio Varela"),
        status = status,
        professionalId = "p1",
        createdAt = 1_760_000_000_000L,
    ),
    contact = if (withContact) {
        Contact("Carlos Gómez", "11 5555-0101", "https://wa.me/5491155550101", Reputation(45, 10))
    } else {
        null
    },
    review = review,
    distanceKm = null,
    viewerIsAssignee = false,
)

@Preview(showBackground = true)
@Composable
private fun ClientRequestDetailOpenPreview() {
    FixItTheme {
        ClientRequestDetailScreen(
            uiState = ClientRequestDetailUiState.Success(previewDetail(RequestStatus.OPEN, withContact = false)),
            onBack = {},
            onCompleteClick = {},
            onCancelConfirm = {},
            onRetry = {},
            onMessageShown = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ClientRequestDetailCompletedPreview() {
    val review = Review("r1", "p1", "c1", rating = 5, comment = "Muy prolijo", createdAt = 1_760_000_000_000L)
    FixItTheme {
        ClientRequestDetailScreen(
            uiState = ClientRequestDetailUiState.Success(previewDetail(RequestStatus.COMPLETED, withContact = true, review)),
            onBack = {},
            onCompleteClick = {},
            onCancelConfirm = {},
            onRetry = {},
            onMessageShown = {},
        )
    }
}
