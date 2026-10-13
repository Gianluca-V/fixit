package com.vespelabs.fixit.feature.professional.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vespelabs.fixit.core.domain.model.User
import com.vespelabs.fixit.core.domain.repository.AuthRepository
import com.vespelabs.fixit.core.domain.repository.UserRepository
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProProfileUiState {
    data object Loading : ProProfileUiState

    /** [user] has its professional fields (categories, base, radius, reputation). */
    data class Success(val user: User) : ProProfileUiState

    data class Error(val error: DomainError) : ProProfileUiState
}

/**
 * Root of the "Perfil" tab (no use case, design §7): observes the signed-in user, so a saved edit
 * and a new review show up by themselves. Errors arrive as values of the flow, so there is no `.catch` (D6).
 */
@HiltViewModel
class ProProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ProProfileUiState>(ProProfileUiState.Loading)
    val uiState: StateFlow<ProProfileUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    init {
        observe()
    }

    fun onRetry() {
        _uiState.value = ProProfileUiState.Loading
        observe()
    }

    private fun observe() {
        job?.cancel()
        job = viewModelScope.launch {
            val userId = authRepository.currentUserId()
            if (userId == null) {
                _uiState.value = ProProfileUiState.Error(DomainError.Unauthorized)
                return@launch
            }
            userRepository.observe(userId).collect { result ->
                _uiState.value = when (result) {
                    is AppResult.Success -> ProProfileUiState.Success(result.data)
                    is AppResult.Error -> ProProfileUiState.Error(result.error)
                }
            }
        }
    }

    /** The session gate in `:app` reacts by itself to the signed-out session. */
    fun onSignOutClick() = authRepository.signOut()
}
