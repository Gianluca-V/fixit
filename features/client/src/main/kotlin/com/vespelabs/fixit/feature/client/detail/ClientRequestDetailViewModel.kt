package com.vespelabs.fixit.feature.client.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vespelabs.fixit.core.domain.model.RequestDetail
import com.vespelabs.fixit.core.domain.model.RequestStatus
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.domain.usecase.CancelRequestUseCase
import com.vespelabs.fixit.core.domain.usecase.ObserveRequestDetailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ClientRequestDetailUiState {
    data object Loading : ClientRequestDetailUiState

    /**
     * Live [detail]. [isWorking] covers the cancellation in progress; [message] is a one-shot error
     * cleared with `onMessageShown()`. The actions follow the status table of the spec.
     */
    data class Success(
        val detail: RequestDetail,
        val isWorking: Boolean = false,
        val message: DomainError? = null,
    ) : ClientRequestDetailUiState {
        val status: RequestStatus get() = detail.request.status
        val canCancel: Boolean get() = status == RequestStatus.OPEN || status == RequestStatus.ACCEPTED
        val canComplete: Boolean get() = status == RequestStatus.ACCEPTED
    }

    data class Error(val error: DomainError) : ClientRequestDetailUiState
}

/**
 * Detail of one of the client's requests. The id comes from the navigation key through an
 * idempotent [load] (D4). Unauthorized / NotFound / Network arrive as values of the flow (D6).
 */
@HiltViewModel
class ClientRequestDetailViewModel @Inject constructor(
    private val observeDetail: ObserveRequestDetailUseCase,
    private val cancelRequest: CancelRequestUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ClientRequestDetailUiState>(ClientRequestDetailUiState.Loading)
    val uiState: StateFlow<ClientRequestDetailUiState> = _uiState.asStateFlow()

    private var requestId: String? = null
    private var job: Job? = null

    /** Ignores repeated calls with the same id (LaunchedEffect runs again after a rotation). */
    fun load(id: String) {
        if (id == requestId) return
        requestId = id
        job?.cancel()
        job = viewModelScope.launch {
            observeDetail(id).collect { result ->
                _uiState.update { old ->
                    when (result) {
                        is AppResult.Error -> ClientRequestDetailUiState.Error(result.error)
                        is AppResult.Success ->
                            if (old is ClientRequestDetailUiState.Success) {
                                old.copy(detail = result.data) // keeps isWorking / message
                            } else {
                                ClientRequestDetailUiState.Success(result.data)
                            }
                    }
                }
            }
        }
    }

    fun onRetry() {
        val id = requestId ?: return
        requestId = null
        _uiState.value = ClientRequestDetailUiState.Loading
        load(id)
    }

    /** Called after the confirmation dialog. The new status arrives through the live detail. */
    fun onCancelConfirm() {
        val id = requestId ?: return
        val current = _uiState.value as? ClientRequestDetailUiState.Success ?: return
        if (current.isWorking || !current.canCancel) return
        updateSuccess { it.copy(isWorking = true) }
        viewModelScope.launch {
            val result = cancelRequest(id)
            updateSuccess { it.copy(isWorking = false, message = (result as? AppResult.Error)?.error) }
        }
    }

    fun onMessageShown() = updateSuccess { it.copy(message = null) }

    private fun updateSuccess(transform: (ClientRequestDetailUiState.Success) -> ClientRequestDetailUiState.Success) =
        _uiState.update { if (it is ClientRequestDetailUiState.Success) transform(it) else it }
}
