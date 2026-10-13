package com.vespelabs.fixit.feature.professional.myjobs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import com.vespelabs.fixit.feature.professional.R
import java.text.DateFormat
import java.util.Date

/** Root of the "Mis trabajos" tab. The feature never navigates: `:app` passes the callbacks. */
@Composable
fun MyJobsRoute(
    onJobClick: (requestId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyJobsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MyJobsScreen(uiState = uiState, onJobClick = onJobClick, onRetry = viewModel::onRetry, modifier = modifier)
}

@Composable
fun MyJobsScreen(
    uiState: MyJobsUiState,
    onJobClick: (requestId: String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(title = stringResource(R.string.pro_my_jobs_title), modifier = modifier) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (uiState) {
                MyJobsUiState.Loading -> LoadingContent()
                is MyJobsUiState.Error -> ErrorContent(error = uiState.error, onRetry = onRetry)
                is MyJobsUiState.Success ->
                    if (uiState.active.isEmpty() && uiState.finished.isEmpty()) {
                        EmptyJobs()
                    } else {
                        JobList(state = uiState, onJobClick = onJobClick)
                    }
            }
        }
    }
}

@Composable
private fun JobList(state: MyJobsUiState.Success, onJobClick: (String) -> Unit) {
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        section(R.string.pro_my_jobs_active, state.active, dateFormat, onJobClick)
        section(R.string.pro_my_jobs_finished, state.finished, dateFormat, onJobClick)
    }
}

private fun LazyListScope.section(
    title: Int,
    jobs: List<RepairRequest>,
    dateFormat: DateFormat,
    onJobClick: (String) -> Unit,
) {
    if (jobs.isEmpty()) return
    item(key = "header-$title") {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
    }
    items(jobs, key = { it.id }) { job -> JobCard(job, dateFormat, onClick = { onJobClick(job.id) }) }
}

/** Shows only the status: the received rating is in the detail, not in the list (spec). */
@Composable
private fun JobCard(job: RepairRequest, dateFormat: DateFormat, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(job.category.label(), style = MaterialTheme.typography.titleMedium)
                if (job.status == RequestStatus.CANCELLED) {
                    Text(
                        text = stringResource(R.string.pro_detail_cancelled_by_client),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    StatusBadge(job.status)
                }
            }
            Text(
                text = job.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            job.acceptedAt?.let {
                Text(
                    text = stringResource(R.string.pro_my_jobs_accepted_on, dateFormat.format(Date(it))),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun EmptyJobs() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.pro_my_jobs_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.pro_my_jobs_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

private fun previewJob(id: String, status: RequestStatus, category: ServiceCategory) = RepairRequest(
    id = id,
    clientId = "c1",
    category = category,
    description = "La canilla de la cocina pierde agua desde hace dos días y no cierra del todo.",
    photoUrls = emptyList(),
    place = Place(GeoPoint(-34.8065, -58.2788), "Av. San Martín 2000, Florencio Varela"),
    status = status,
    professionalId = "p1",
    createdAt = 1_760_000_000_000L,
    acceptedAt = 1_760_100_000_000L,
)

@Preview(showBackground = true)
@Composable
private fun MyJobsScreenPreview() {
    FixItTheme {
        MyJobsScreen(
            uiState = MyJobsUiState.Success(
                active = listOf(previewJob("r3", RequestStatus.ACCEPTED, ServiceCategory.PLUMBING)),
                finished = listOf(
                    previewJob("r2", RequestStatus.COMPLETED, ServiceCategory.ELECTRICITY),
                    previewJob("r1", RequestStatus.CANCELLED, ServiceCategory.GAS),
                ),
            ),
            onJobClick = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MyJobsScreenEmptyPreview() {
    FixItTheme { MyJobsScreen(uiState = MyJobsUiState.Success(emptyList(), emptyList()), onJobClick = {}, onRetry = {}) }
}
