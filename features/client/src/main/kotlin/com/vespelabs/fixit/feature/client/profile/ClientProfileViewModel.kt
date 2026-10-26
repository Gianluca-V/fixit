package com.vespelabs.fixit.feature.client.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vespelabs.fixit.core.domain.model.ThemeMode
import com.vespelabs.fixit.core.domain.model.User
import com.vespelabs.fixit.core.domain.repository.AuthRepository
import com.vespelabs.fixit.core.domain.repository.ThemePreferences
import com.vespelabs.fixit.core.domain.repository.UserRepository
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ClientProfileUiState {
    data object Loading : ClientProfileUiState

    data class Success(val user: User) : ClientProfileUiState

    data class Error(val error: DomainError) : ClientProfileUiState
}

/**
 * Root of the "Perfil" tab (no use case, design §7): observes the signed-in user, so a saved edit
 * shows up by itself. Errors arrive as values of the flow, so there is no `.catch` (D6).
 */
@HiltViewModel
class ClientProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val themePreferences: ThemePreferences,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ClientProfileUiState>(ClientProfileUiState.Loading)
    val uiState: StateFlow<ClientProfileUiState> = _uiState.asStateFlow()

    /** Device state, not screen state: the repository's own flow, so both profile screens agree. */
    val themeMode: StateFlow<ThemeMode> = themePreferences.mode

    init {
        viewModelScope.launch {
            val userId = authRepository.currentUserId()
            if (userId == null) {
                _uiState.value = ClientProfileUiState.Error(DomainError.Unauthorized)
                return@launch
            }
            userRepository.observe(userId).collect { result ->
                _uiState.value = when (result) {
                    is AppResult.Success -> ClientProfileUiState.Success(result.data)
                    is AppResult.Error -> ClientProfileUiState.Error(result.error)
                }
            }
        }
    }

    /** The session gate in `:app` reacts by itself to the signed-out session. */
    fun onSignOutClick() = authRepository.signOut()

    fun onThemeModeChange(mode: ThemeMode) = themePreferences.setMode(mode)
}
