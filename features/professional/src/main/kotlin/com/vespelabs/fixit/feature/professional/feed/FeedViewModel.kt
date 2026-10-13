package com.vespelabs.fixit.feature.professional.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vespelabs.fixit.core.domain.model.FeedItem
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.domain.usecase.ObserveFeedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface FeedUiState {
    data object Loading : FeedUiState

    /** Already filtered and ordered by the use case: most recent first. */
    data class Success(val items: List<FeedItem>) : FeedUiState

    data class Error(val error: DomainError) : FeedUiState
}

/**
 * Live feed of the signed-in professional. Profile edits (categories, base, radius) re-filter it
 * through the use case. Errors arrive as values of the flow, so there is no `.catch` (D6).
 */
@HiltViewModel
class FeedViewModel @Inject constructor(
    private val observeFeed: ObserveFeedUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<FeedUiState>(FeedUiState.Loading)
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    init {
        observe()
    }

    fun onRetry() {
        _uiState.value = FeedUiState.Loading
        observe()
    }

    private fun observe() {
        job?.cancel()
        job = viewModelScope.launch {
            observeFeed().collect { result ->
                _uiState.value = when (result) {
                    is AppResult.Success -> FeedUiState.Success(result.data)
                    is AppResult.Error -> FeedUiState.Error(result.error)
                }
            }
        }
    }
}
