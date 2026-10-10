package ar.edu.unaj.fixit.feature.client.complete

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.unaj.fixit.core.domain.model.Limits
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError
import ar.edu.unaj.fixit.core.domain.result.ValidationError
import ar.edu.unaj.fixit.core.domain.usecase.CompleteJobUseCase
import ar.edu.unaj.fixit.core.domain.usecase.ObserveRequestDetailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * "Completar y calificar" form. [professionalName] comes from the request detail; when it cannot be
 * loaded, [loadError] is shown with "Reintentar". [rating] 0 means no star selected yet.
 * [message] is a one-shot error cleared with `onMessageShown()`; [completed] is a one-shot navigation
 * event cleared with `onNavigated()`.
 */
data class CompleteJobUiState(
    val isLoading: Boolean = true,
    val professionalName: String? = null,
    val loadError: DomainError? = null,
    val rating: Int = 0,
    val comment: String = "",
    val isSubmitting: Boolean = false,
    val validationError: ValidationError? = null,
    val message: DomainError? = null,
    val completed: Boolean = false,
) {
    val canSubmit: Boolean get() = rating >= Limits.RATING_MIN && !isSubmitting
}

@HiltViewModel
class CompleteJobViewModel @Inject constructor(
    private val observeDetail: ObserveRequestDetailUseCase,
    private val completeJob: CompleteJobUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CompleteJobUiState())
    val uiState: StateFlow<CompleteJobUiState> = _uiState.asStateFlow()

    private var requestId: String? = null
    private var job: Job? = null

    /** Idempotent (D4): the assigned professional's name is the counterpart of the live detail. */
    fun load(id: String) {
        if (id == requestId) return
        requestId = id
        job?.cancel()
        job = viewModelScope.launch {
            observeDetail(id).collect { result ->
                _uiState.update {
                    when (result) {
                        is AppResult.Success -> it.copy(
                            isLoading = false,
                            loadError = null,
                            professionalName = result.data.contact?.name ?: it.professionalName,
                        )
                        is AppResult.Error -> it.copy(isLoading = false, loadError = result.error)
                    }
                }
            }
        }
    }

    fun onRetry() {
        val id = requestId ?: return
        requestId = null
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        load(id)
    }

    fun onRatingChange(rating: Int) = _uiState.update { it.copy(rating = rating, validationError = null) }

    fun onCommentChange(comment: String) = _uiState.update { it.copy(comment = comment, validationError = null) }

    /** On failure the stars and the comment stay, so the user can retry. */
    fun onSubmit() {
        val id = requestId ?: return
        val form = _uiState.value
        if (!form.canSubmit) return
        _uiState.update { it.copy(isSubmitting = true, validationError = null) }
        viewModelScope.launch {
            val result = completeJob(id, form.rating, form.comment)
            _uiState.update {
                when (result) {
                    is AppResult.Success -> it.copy(isSubmitting = false, completed = true)
                    is AppResult.Error -> it.copy(
                        isSubmitting = false,
                        validationError = (result.error as? DomainError.Validation)?.error,
                        message = result.error.takeUnless { e -> e is DomainError.Validation },
                    )
                }
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun onNavigated() = _uiState.update { it.copy(completed = false) }
}
