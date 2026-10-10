package ar.edu.unaj.fixit.feature.client.myrequests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.unaj.fixit.core.domain.model.RepairRequest
import ar.edu.unaj.fixit.core.domain.repository.AuthRepository
import ar.edu.unaj.fixit.core.domain.repository.RepairRequestRepository
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MyRequestsUiState {
    data object Loading : MyRequestsUiState

    /** Already ordered by the repository: most recent first. */
    data class Success(val requests: List<RepairRequest>) : MyRequestsUiState

    data class Error(val error: DomainError) : MyRequestsUiState
}

/**
 * "Mis solicitudes" (no use case, design §7): observes the requests of the signed-in client.
 * Errors arrive as values of the flow, so there is no `.catch` (D6).
 */
@HiltViewModel
class MyRequestsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val requestRepository: RepairRequestRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<MyRequestsUiState>(MyRequestsUiState.Loading)
    val uiState: StateFlow<MyRequestsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val clientId = authRepository.currentUserId()
            if (clientId == null) {
                _uiState.value = MyRequestsUiState.Error(DomainError.Unauthorized)
                return@launch
            }
            requestRepository.observeByClient(clientId).collect { result ->
                _uiState.value = when (result) {
                    is AppResult.Success -> MyRequestsUiState.Success(result.data)
                    is AppResult.Error -> MyRequestsUiState.Error(result.error)
                }
            }
        }
    }
}
