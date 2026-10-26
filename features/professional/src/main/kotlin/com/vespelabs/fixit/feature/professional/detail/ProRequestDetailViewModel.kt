package com.vespelabs.fixit.feature.professional.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vespelabs.fixit.core.domain.model.RequestDetail
import com.vespelabs.fixit.core.domain.model.RequestStatus
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.domain.usecase.AcceptRequestUseCase
import com.vespelabs.fixit.core.domain.usecase.ObserveRequestDetailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ProRequestDetailUiState {
    data object Loading : ProRequestDetailUiState

    /**
     * Live [detail]. [isAccepting] covers the acceptance in progress; [message] is a one-shot error
     * cleared with `onMessageShown()` (a lost race is shown as a dialog, see [isRaceLost]);
     * [acceptedId] is a one-shot navigation event cleared with `onNavigated()`.
     * The content follows the status table of the spec (design §9).
     */
    data class Success(
        val detail: RequestDetail,
        val isAccepting: Boolean = false,
        val message: DomainError? = null,
        val acceptedId: String? = null,
    ) : ProRequestDetailUiState {
        private val status: RequestStatus get() = detail.request.status

        val canAccept: Boolean get() = status == RequestStatus.OPEN

        /** Taken by someone else or cancelled while open: "Esta solicitud ya no está disponible". */
        val isUnavailable: Boolean get() = status != RequestStatus.OPEN && !detail.viewerIsAssignee

        /** The viewer had accepted it and the client cancelled: "Cancelada por el cliente". */
        val isCancelledByClient: Boolean get() = status == RequestStatus.CANCELLED && detail.viewerIsAssignee

        /** The job is the viewer's and still to be done: the map shows where to go. */
        val showsDestinationMap: Boolean get() = status == RequestStatus.ACCEPTED && detail.viewerIsAssignee

        /** Another professional won (AlreadyTaken) or the client cancelled meanwhile (InvalidTransition). */
        val isRaceLost: Boolean get() = message == DomainError.AlreadyTaken || message is DomainError.InvalidTransition
    }

    data class Error(val error: DomainError) : ProRequestDetailUiState
}

/**
 * Detail of a request for a professional. The id comes from the navigation key through an
 * idempotent [load] (D4). Unauthorized / NotFound / Network arrive as values of the flow (D6).
 */
@HiltViewModel
class ProRequestDetailViewModel @Inject constructor(
    private val observeDetail: ObserveRequestDetailUseCase,
    private val acceptRequest: AcceptRequestUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ProRequestDetailUiState>(ProRequestDetailUiState.Loading)
    val uiState: StateFlow<ProRequestDetailUiState> = _uiState.asStateFlow()

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
                        is AppResult.Error -> ProRequestDetailUiState.Error(result.error)
                        is AppResult.Success ->
                            if (old is ProRequestDetailUiState.Success) {
                                old.copy(detail = result.data) // keeps isAccepting / message / acceptedId
                            } else {
                                ProRequestDetailUiState.Success(result.data)
                            }
                    }
                }
            }
        }
    }

    fun onRetry() {
        val id = requestId ?: return
        requestId = null
        _uiState.value = ProRequestDetailUiState.Loading
        load(id)
    }

    /** Called after the confirmation dialog. On success the new status also arrives through the live detail. */
    fun onAcceptConfirm() {
        val id = requestId ?: return
        val current = _uiState.value as? ProRequestDetailUiState.Success ?: return
        if (current.isAccepting || !current.canAccept) return
        updateSuccess { it.copy(isAccepting = true) }
        viewModelScope.launch {
            val result = acceptRequest(id)
            updateSuccess {
                when (result) {
                    is AppResult.Success -> it.copy(isAccepting = false, acceptedId = id)
                    is AppResult.Error -> it.copy(isAccepting = false, message = result.error)
                }
            }
        }
    }

    fun onMessageShown() = updateSuccess { it.copy(message = null) }

    fun onNavigated() = updateSuccess { it.copy(acceptedId = null) }

    private fun updateSuccess(transform: (ProRequestDetailUiState.Success) -> ProRequestDetailUiState.Success) =
        _uiState.update { if (it is ProRequestDetailUiState.Success) transform(it) else it }
}
