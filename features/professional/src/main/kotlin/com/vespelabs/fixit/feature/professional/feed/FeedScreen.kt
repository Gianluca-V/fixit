package com.vespelabs.fixit.feature.professional.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vespelabs.fixit.core.domain.model.FeedItem
import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.model.RepairRequest
import com.vespelabs.fixit.core.domain.model.RequestStatus
import com.vespelabs.fixit.core.domain.model.ServiceCategory
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.ui.components.ErrorContent
import com.vespelabs.fixit.core.ui.components.LoadingContent
import com.vespelabs.fixit.core.ui.components.ScreenScaffold
import com.vespelabs.fixit.core.ui.text.label
import com.vespelabs.fixit.core.ui.theme.FixItTheme
import com.vespelabs.fixit.feature.professional.R
import com.vespelabs.fixit.feature.professional.common.distanceText
import java.text.DateFormat
import java.util.Date

/** Root of the "Feed" tab. The feature never navigates: `:app` passes the callbacks. */
@Composable
fun FeedRoute(
    onRequestClick: (requestId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FeedScreen(
        uiState = uiState,
        onRequestClick = onRequestClick,
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )
}

@Composable
fun FeedScreen(
    uiState: FeedUiState,
    onRequestClick: (requestId: String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(title = stringResource(R.string.pro_feed_title), modifier = modifier) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (uiState) {
                FeedUiState.Loading -> LoadingContent()
                is FeedUiState.Error -> ErrorContent(error = uiState.error, onRetry = onRetry)
                is FeedUiState.Success ->
                    if (uiState.items.isEmpty()) {
                        EmptyFeed()
                    } else {
                        FeedList(items = uiState.items, onRequestClick = onRequestClick)
                    }
            }
        }
    }
}

@Composable
private fun FeedList(items: List<FeedItem>, onRequestClick: (String) -> Unit) {
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.request.id }) { item ->
            val request = item.request
            Card(onClick = { onRequestClick(request.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(request.category.label(), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = distanceText(item.distanceKm),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Text(
                        text = request.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.pro_published_on, dateFormat.format(Date(request.createdAt))),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyFeed() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.pro_feed_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.pro_feed_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

private fun previewItem(id: String, km: Double, category: ServiceCategory) = FeedItem(
    request = RepairRequest(
        id = id,
        clientId = "c1",
        category = category,
        description = "La canilla de la cocina pierde agua desde hace dos días y no cierra del todo.",
        photoUrls = emptyList(),
        place = Place(GeoPoint(-34.8065, -58.2788), "Av. San Martín 2000, Florencio Varela"),
        status = RequestStatus.OPEN,
        createdAt = 1_760_000_000_000L,
    ),
    distanceKm = km,
)

@Preview(showBackground = true)
@Composable
private fun FeedScreenPreview() {
    FixItTheme {
        FeedScreen(
            uiState = FeedUiState.Success(
                listOf(
                    previewItem("r2", 2.5, ServiceCategory.PLUMBING),
                    previewItem("r1", 8.0, ServiceCategory.ELECTRICITY),
                ),
            ),
            onRequestClick = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FeedScreenEmptyPreview() {
    FixItTheme { FeedScreen(uiState = FeedUiState.Success(emptyList()), onRequestClick = {}, onRetry = {}) }
}

@Preview(showBackground = true)
@Composable
private fun FeedScreenErrorPreview() {
    FixItTheme { FeedScreen(uiState = FeedUiState.Error(DomainError.Network), onRequestClick = {}, onRetry = {}) }
}
