package ar.edu.unaj.fixit.feature.auth.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError
import ar.edu.unaj.fixit.core.domain.result.ValidationError
import ar.edu.unaj.fixit.core.domain.usecase.SignInUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Sign-in form. [validationError] is shown next to its field ([ValidationError.field]); any other
 * error is a one-shot [message] that the UI clears with [SignInViewModel.onMessageShown].
 */
data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val validationError: ValidationError? = null,
    val message: DomainError? = null,
)

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val signIn: SignInUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) = _uiState.update { it.copy(email = email) }

    fun onPasswordChange(password: String) = _uiState.update { it.copy(password = password) }

    fun onSubmit() {
        val form = _uiState.value
        if (form.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, validationError = null) }
        viewModelScope.launch {
            val error = (signIn(form.email, form.password) as? AppResult.Error)?.error
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
