package com.vespelabs.fixit.feature.professional.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vespelabs.fixit.core.domain.repository.AuthRepository
import com.vespelabs.fixit.core.domain.repository.UserRepository
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.domain.result.ValidationError
import com.vespelabs.fixit.core.domain.usecase.GetCurrentPlaceUseCase
import com.vespelabs.fixit.core.domain.usecase.UpdateProfileUseCase
import com.vespelabs.fixit.core.ui.components.ProfileFormState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * "Editar perfil" of a professional. [form] is preloaded once with the current values (including
 * categories, base and radius); [email] is read-only and the role is never shown as editable.
 * When the user cannot be loaded, [loadError] is shown with "Reintentar". [validationError] is shown
 * next to its field; any other error is a one-shot [message] cleared with `onMessageShown()`;
 * [saved] is a one-shot navigation event cleared with `onNavigated()`.
 */
data class ProEditProfileUiState(
    val isLoading: Boolean = true,
    val loadError: DomainError? = null,
    val email: String = "",
    val form: ProfileFormState = ProfileFormState(),
    val isLocating: Boolean = false,
    val isSaving: Boolean = false,
    val validationError: ValidationError? = null,
    val message: DomainError? = null,
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = !isLoading && loadError == null && !isSaving && !isLocating
}

@HiltViewModel
class ProEditProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val updateProfile: UpdateProfileUseCase,
    private val getCurrentPlace: GetCurrentPlaceUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProEditProfileUiState())
    val uiState: StateFlow<ProEditProfileUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** A single read (not an observation), so a later change of the user never overwrites what is typed. */
    private fun load() {
        viewModelScope.launch {
            val userId = authRepository.currentUserId()
            val result = if (userId == null) AppResult.Error(DomainError.Unauthorized) else userRepository.get(userId)
            _uiState.update {
                when (result) {
                    is AppResult.Success -> it.copy(
                        isLoading = false,
                        loadError = null,
                        email = result.data.email,
                        form = ProfileFormState.from(result.data),
                    )
                    is AppResult.Error -> it.copy(isLoading = false, loadError = result.error)
                }
            }
        }
    }

    fun onRetry() {
        if (_uiState.value.loadError == null) return
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        load()
    }

    fun onFormChange(form: ProfileFormState) = _uiState.update { it.copy(form = form, validationError = null) }

    /** The base comes from the device location, never typed as coordinates (spec). */
    fun onUseLocation() {
        if (_uiState.value.isLocating) return
        _uiState.update { it.copy(isLocating = true) }
        viewModelScope.launch {
            val result = getCurrentPlace()
            _uiState.update {
                when (result) {
                    is AppResult.Success -> it.copy(isLocating = false, form = it.form.copy(base = result.data), validationError = null)
                    is AppResult.Error -> it.copy(isLocating = false, message = result.error)
                }
            }
        }
    }

    /** Rules and trimming live in [UpdateProfileUseCase]; on failure what was typed stays, so the user can retry. */
    fun onSave() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true, validationError = null) }
        viewModelScope.launch {
            val result = updateProfile(state.form.toProfileForm())
            _uiState.update {
                when (result) {
                    is AppResult.Success -> it.copy(isSaving = false, saved = true)
                    is AppResult.Error -> it.copy(
                        isSaving = false,
                        validationError = (result.error as? DomainError.Validation)?.error,
                        message = result.error.takeUnless { e -> e is DomainError.Validation },
                    )
                }
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun onNavigated() = _uiState.update { it.copy(saved = false) }
}
