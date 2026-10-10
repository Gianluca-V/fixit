package ar.edu.unaj.fixit.feature.auth.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError
import ar.edu.unaj.fixit.core.domain.usecase.SignInUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// TODO(task 1.7.1/1.7.2): TEMPORARY minimal ViewModel so the app can be used with the seed users
// before the real sign-in form exists. Replace it by the TDD version (SignInViewModelTest first):
// form fields, validationError next to the field, isSubmitting, one-shot message.

data class SignInUiState(
    val isSubmitting: Boolean = false,
    val message: DomainError? = null,
)

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val signIn: SignInUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    fun onSignIn(email: String, password: String) {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            val result = signIn(email, password)
            _uiState.update {
                it.copy(isSubmitting = false, message = (result as? AppResult.Error)?.error)
            }
            // On success nothing else to do: the session gate in :app reacts by itself.
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
}
