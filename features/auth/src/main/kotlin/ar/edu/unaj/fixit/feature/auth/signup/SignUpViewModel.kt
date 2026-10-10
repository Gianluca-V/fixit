package ar.edu.unaj.fixit.feature.auth.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.unaj.fixit.core.domain.model.SignUpForm
import ar.edu.unaj.fixit.core.domain.model.UserRole
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError
import ar.edu.unaj.fixit.core.domain.result.ValidationError
import ar.edu.unaj.fixit.core.domain.usecase.GetCurrentPlaceUseCase
import ar.edu.unaj.fixit.core.domain.usecase.SignUpUseCase
import ar.edu.unaj.fixit.core.ui.components.ProfileFormState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Sign-up form. The role is chosen here and never changes afterwards. [validationError] is shown
 * next to its field; any other error is a one-shot [message] cleared with `onMessageShown()`.
 */
data class SignUpUiState(
    val email: String = "",
    val password: String = "",
    val role: UserRole? = null,
    val profile: ProfileFormState = ProfileFormState(),
    val isSubmitting: Boolean = false,
    val isLocating: Boolean = false,
    val validationError: ValidationError? = null,
    val message: DomainError? = null,
) {
    val showProfessionalFields: Boolean get() = role == UserRole.PROFESSIONAL
}

@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val signUp: SignUpUseCase,
    private val getCurrentPlace: GetCurrentPlaceUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SignUpUiState())
    val uiState: StateFlow<SignUpUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) = _uiState.update { it.copy(email = email) }

    fun onPasswordChange(password: String) = _uiState.update { it.copy(password = password) }

    fun onRoleSelect(role: UserRole) = _uiState.update { it.copy(role = role) }

    fun onProfileChange(profile: ProfileFormState) = _uiState.update { it.copy(profile = profile) }

    fun onUseLocation() {
        if (_uiState.value.isLocating) return
        _uiState.update { it.copy(isLocating = true) }
        viewModelScope.launch {
            val result = getCurrentPlace()
            _uiState.update {
                when (result) {
                    is AppResult.Success -> it.copy(isLocating = false, profile = it.profile.copy(base = result.data))
                    is AppResult.Error -> it.copy(isLocating = false, message = result.error)
                }
            }
        }
    }

    fun onSubmit() {
        val form = _uiState.value
        if (form.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, validationError = null) }
        viewModelScope.launch {
            // The use case drops the professional fields of a CLIENT.
            val result = signUp(SignUpForm(form.email, form.password, form.role, form.profile.toProfileForm()))
            val error = (result as? AppResult.Error)?.error
            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    validationError = (error as? DomainError.Validation)?.error,
                    message = error?.takeUnless { e -> e is DomainError.Validation },
                )
            }
            // On success there is nothing else to do: the session gate in :app reacts by itself.
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
}
