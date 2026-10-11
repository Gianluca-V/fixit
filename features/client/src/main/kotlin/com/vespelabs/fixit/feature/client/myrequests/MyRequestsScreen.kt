package com.vespelabs.fixit.feature.client.myrequests

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
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
import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.model.RepairRequest
import com.vespelabs.fixit.core.domain.model.RequestStatus
import com.vespelabs.fixit.core.domain.model.ServiceCategory
import com.vespelabs.fixit.core.ui.components.ErrorContent
import com.vespelabs.fixit.core.ui.components.LoadingContent
import com.vespelabs.fixit.core.ui.components.ScreenScaffold
import com.vespelabs.fixit.core.ui.components.StatusBadge
import com.vespelabs.fixit.core.ui.text.label
import com.vespelabs.fixit.core.ui.theme.FixItTheme
import com.vespelabs.fixit.feature.client.R
import java.text.DateFormat
import java.util.Date

/** Root of the "Solicitudes" tab. The feature never navigates: `:app` passes the callbacks. */
@Composable
fun MyRequestsRoute(
    onCreateClick: () -> Unit,
    onRequestClick: (requestId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyRequestsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MyRequestsScreen(
        uiState = uiState,
        onCreateClick = onCreateClick,
        onRequestClick = onRequestClick,
        modifier = modifier,
    )
}

@Composable
fun MyRequestsScreen(
    uiState: MyRequestsUiState,
    onCreateClick: () -> Unit,
    onRequestClick: (requestId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(title = stringResource(R.string.client_my_requests_title), modifier = modifier) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (uiState) {
                MyRequestsUiState.Loading -> LoadingContent()
                is MyRequestsUiState.Error -> ErrorContent(error = uiState.error)
                is MyRequestsUiState.Success ->
                    if (uiState.requests.isEmpty()) {
                        EmptyRequests(onCreateClick = onCreateClick)
                    } else {
                        RequestList(requests = uiState.requests, onRequestClick = onRequestClick)
                        ExtendedFloatingActionButton(
                            onClick = onCreateClick,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                        ) {
                            Text(stringResource(R.string.client_new_request))
                        }
                    }
            }
        }
    }
}

@Composable
private fun RequestList(requests: List<RepairRequest>, onRequestClick: (String) -> Unit) {
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    LazyColumn(
        // Bottom padding leaves room for the floating button over the last item.
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(requests, key = { it.id }) { request ->
            Card(onClick = { onRequestClick(request.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(request.category.label(), style = MaterialTheme.typography.titleMedium)
                        StatusBadge(request.status)
                    }
                    Text(
                        text = request.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.client_request_created_on, dateFormat.format(Date(request.createdAt))),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyRequests(onCreateClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.client_my_requests_empty),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onCreateClick) { Text(stringResource(R.string.client_new_request)) }
    }
}

private fun previewRequest(id: String, status: RequestStatus, category: ServiceCategory) = RepairRequest(
    id = id,
    clientId = "c1",
    category = category,
    description = "La canilla de la cocina pierde agua desde hace dos días y no cierra del todo.",
    photoUrls = emptyList(),
    place = Place(GeoPoint(-34.8065, -58.2788), "Av. San Martín 2000, Florencio Varela"),
    status = status,
    createdAt = 1_760_000_000_000L,
)

@Preview(showBackground = true)
@Composable
private fun MyRequestsScreenPreview() {
    FixItTheme {
        MyRequestsScreen(
            uiState = MyRequestsUiState.Success(
                listOf(
                    previewRequest("r2", RequestStatus.OPEN, ServiceCategory.PLUMBING),
                    previewRequest("r1", RequestStatus.ACCEPTED, ServiceCategory.ELECTRICITY),
                ),
            ),
            onCreateClick = {},
            onRequestClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MyRequestsScreenEmptyPreview() {
    FixItTheme {
        MyRequestsScreen(uiState = MyRequestsUiState.Success(emptyList()), onCreateClick = {}, onRequestClick = {})
    }
}
