package com.vespelabs.fixit.feature.professional.myjobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vespelabs.fixit.core.domain.model.RepairRequest
import com.vespelabs.fixit.core.domain.model.RequestStatus
import com.vespelabs.fixit.core.domain.repository.AuthRepository
import com.vespelabs.fixit.core.domain.repository.RepairRequestRepository
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MyJobsUiState {
    data object Loading : MyJobsUiState

    /** [active] = ACCEPTED, [finished] = COMPLETED or CANCELLED; each newest accepted first. */
    data class Success(val active: List<RepairRequest>, val finished: List<RepairRequest>) : MyJobsUiState

    data class Error(val error: DomainError) : MyJobsUiState
}

/**
 * "Mis trabajos" (no use case, design §7): observes the requests assigned to the signed-in
 * professional. Errors arrive as values of the flow, so there is no `.catch` (D6).
 */
@HiltViewModel
class MyJobsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val requestRepository: RepairRequestRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<MyJobsUiState>(MyJobsUiState.Loading)
    val uiState: StateFlow<MyJobsUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    init {
        observe()
    }

    fun onRetry() {
        _uiState.value = MyJobsUiState.Loading
        observe()
    }

    private fun observe() {
        job?.cancel()
        job = viewModelScope.launch {
            val professionalId = authRepository.currentUserId()
            if (professionalId == null) {
                _uiState.value = MyJobsUiState.Error(DomainError.Unauthorized)
                return@launch
            }
            requestRepository.observeByProfessional(professionalId).collect { result ->
                _uiState.value = when (result) {
                    is AppResult.Success -> result.data.toUiState()
                    is AppResult.Error -> MyJobsUiState.Error(result.error)
                }
            }
        }
    }

    /** The order is decided here, so it does not depend on how each repository sorts. */
    private fun List<RepairRequest>.toUiState(): MyJobsUiState.Success {
        val (active, finished) = sortedByDescending { it.acceptedAt ?: 0L }
            .partition { it.status == RequestStatus.ACCEPTED }
        return MyJobsUiState.Success(active = active, finished = finished)
    }
}
