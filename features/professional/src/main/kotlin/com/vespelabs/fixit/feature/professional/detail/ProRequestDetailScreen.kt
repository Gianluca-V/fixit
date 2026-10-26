package com.vespelabs.fixit.feature.professional.detail

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
import com.vespelabs.fixit.core.ui.map.DestinationMapCard
import com.vespelabs.fixit.core.ui.text.label
import com.vespelabs.fixit.core.ui.theme.FixItTheme
import com.vespelabs.fixit.feature.professional.R
import com.vespelabs.fixit.feature.professional.common.MessageScaffold
import com.vespelabs.fixit.feature.professional.common.distanceText
import com.vespelabs.fixit.feature.professional.common.isRetryable
import java.text.DateFormat
import java.util.Date

/**
 * Detail of a request for a professional; [requestId] comes from the navigation key and is passed to
 * the idempotent `load` (D4). `:app` resolves onAccepted(id) to goBackFrom + selectTab(MY_JOBS) +
 * resetToRoot + navigate(detail); when the race is lost the dialog's confirmation calls [onBack].
 */
@Composable
fun ProRequestDetailRoute(
    requestId: String,
    onBack: () -> Unit,
    onAccepted: (requestId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProRequestDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(requestId) { viewModel.load(requestId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val acceptedId = (uiState as? ProRequestDetailUiState.Success)?.acceptedId
    LaunchedEffect(acceptedId) {
        if (acceptedId != null) {
            onAccepted(acceptedId)
            viewModel.onNavigated()
        }
    }
    ProRequestDetailScreen(
        uiState = uiState,
        onBack = onBack,
        onAcceptConfirm = viewModel::onAcceptConfirm,
        onRetry = viewModel::onRetry,
        onMessageShown = viewModel::onMessageShown,
        modifier = modifier,
    )
}

@Composable
fun ProRequestDetailScreen(
    uiState: ProRequestDetailUiState,
    onBack: () -> Unit,
    onAcceptConfirm: () -> Unit,
    onRetry: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val success = uiState as? ProRequestDetailUiState.Success
    MessageScaffold(
        title = stringResource(R.string.pro_request_detail_title),
        // A lost race is a dialog, not a snackbar.
        message = success?.message?.takeUnless { success.isRaceLost },
        onMessageShown = onMessageShown,
        modifier = modifier,
        onBack = onBack,
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (uiState) {
            ProRequestDetailUiState.Loading -> LoadingContent(contentModifier)
            // NotFound / Unauthorized: the top bar offers going back; retrying only helps other errors.
            is ProRequestDetailUiState.Error -> ErrorContent(
                error = uiState.error,
                modifier = contentModifier,
                onRetry = onRetry.takeIf { uiState.error.isRetryable() },
            )
            is ProRequestDetailUiState.Success -> DetailContent(
                state = uiState,
                onAcceptConfirm = onAcceptConfirm,
                modifier = contentModifier,
            )
        }
    }

    if (success != null && success.isRaceLost) {
        RaceLostDialog(
            error = success.message,
            onConfirm = {
                onMessageShown()
                onBack()
            },
        )
    }
}

@Composable
private fun DetailContent(
    state: ProRequestDetailUiState.Success,
    onAcceptConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = state.detail
    val request = detail.request
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    var confirmingAccept by rememberSaveable { mutableStateOf(false) }

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
        // Once the job is the viewer's, the address comes with the map of where to go.
        if (state.showsDestinationMap) {
            DestinationMapCard(request.place, Modifier.fillMaxWidth())
        } else {
            Text(request.place.address, style = MaterialTheme.typography.bodyMedium)
        }
        detail.distanceKm?.let { Text(distanceText(it), style = MaterialTheme.typography.labelLarge) }
        Text(
            text = stringResource(R.string.pro_published_on, dateFormat.format(Date(request.createdAt))),
            style = MaterialTheme.typography.bodySmall,
        )

        StatusContent(state)

        if (state.canAccept) {
            Button(
                onClick = { confirmingAccept = true },
                enabled = !state.isAccepting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isAccepting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.pro_detail_accept))
                }
            }
        }
    }

    // A live update that makes the request unavailable also closes the dialog: confirming would do nothing.
    if (confirmingAccept && state.canAccept) {
        AcceptDialog(
            onConfirm = {
                confirmingAccept = false
                onAcceptConfirm()
            },
            onDismiss = { confirmingAccept = false },
        )
    }
}

/** The extra content of each status and viewer (spec table): notice, contact, review or cancelled text. */
@Composable
private fun StatusContent(state: ProRequestDetailUiState.Success) {
    when {
        state.isUnavailable -> InfoText(stringResource(R.string.pro_detail_unavailable))
        state.isCancelledByClient -> InfoText(stringResource(R.string.pro_detail_cancelled_by_client))
        else -> {
            // Only the assignee of an ACCEPTED/COMPLETED request gets a contact (ContactRules).
            state.detail.contact?.let { ContactCard(it, Modifier.fillMaxWidth()) }
            state.detail.review?.let { ReviewCard(it) }
        }
    }
}

@Composable
private fun InfoText(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
}

/** Read-only: rating is one-way, the professional has no action here (job-completion). */
@Composable
private fun ReviewCard(review: Review) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.pro_detail_review), style = MaterialTheme.typography.labelLarge)
            RatingBar(rating = review.rating)
            review.comment?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun AcceptDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pro_detail_accept_title)) },
        text = { Text(stringResource(R.string.pro_detail_accept_text)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.pro_detail_accept_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.pro_detail_accept_dismiss)) } },
    )
}

/** Not dismissable from outside: the only way out is back to the Feed. */
@Composable
private fun RaceLostDialog(error: DomainError?, onConfirm: () -> Unit) {
    val text = if (error == DomainError.AlreadyTaken) R.string.pro_race_lost_taken else R.string.pro_race_lost_unavailable
    AlertDialog(
        onDismissRequest = {},
        text = { Text(stringResource(text)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.pro_race_lost_confirm)) } },
    )
}

private fun previewDetail(
    status: RequestStatus,
    viewerIsAssignee: Boolean,
    withContact: Boolean = false,
    review: Review? = null,
) = RequestDetail(
    request = RepairRequest(
        id = "r1",
        clientId = "c1",
        category = ServiceCategory.PLUMBING,
        description = "La canilla de la cocina pierde agua desde hace dos días y no cierra del todo.",
        photoUrls = SamplePhotos.take(2),
        place = Place(GeoPoint(-34.8065, -58.2788), "Av. San Martín 2000, Florencio Varela"),
        status = status,
        professionalId = if (status == RequestStatus.OPEN) null else "p1",
        createdAt = 1_760_000_000_000L,
    ),
    contact = if (withContact) Contact("Ana López", "11 5555-0001", "https://wa.me/5491155550001", null) else null,
    review = review,
    distanceKm = 2.5,
    viewerIsAssignee = viewerIsAssignee,
)

@Preview(showBackground = true)
@Composable
private fun ProRequestDetailOpenPreview() {
    FixItTheme {
        ProRequestDetailScreen(
            uiState = ProRequestDetailUiState.Success(previewDetail(RequestStatus.OPEN, viewerIsAssignee = false)),
            onBack = {},
            onAcceptConfirm = {},
            onRetry = {},
            onMessageShown = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProRequestDetailCompletedPreview() {
    val review = Review("r1", "p1", "c1", rating = 5, comment = "Muy prolijo", createdAt = 1_760_000_000_000L)
    FixItTheme {
        ProRequestDetailScreen(
            uiState = ProRequestDetailUiState.Success(
                previewDetail(RequestStatus.COMPLETED, viewerIsAssignee = true, withContact = true, review = review),
            ),
            onBack = {},
            onAcceptConfirm = {},
            onRetry = {},
            onMessageShown = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProRequestDetailUnavailablePreview() {
    FixItTheme {
        ProRequestDetailScreen(
            uiState = ProRequestDetailUiState.Success(previewDetail(RequestStatus.ACCEPTED, viewerIsAssignee = false)),
            onBack = {},
            onAcceptConfirm = {},
            onRetry = {},
            onMessageShown = {},
        )
    }
}
