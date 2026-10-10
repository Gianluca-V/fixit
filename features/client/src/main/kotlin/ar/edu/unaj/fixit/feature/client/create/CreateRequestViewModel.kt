package ar.edu.unaj.fixit.feature.client.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.unaj.fixit.core.domain.model.GeoPoint
import ar.edu.unaj.fixit.core.domain.model.Limits
import ar.edu.unaj.fixit.core.domain.model.NewRequest
import ar.edu.unaj.fixit.core.domain.model.Place
import ar.edu.unaj.fixit.core.domain.model.ServiceCategory
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError
import ar.edu.unaj.fixit.core.domain.result.ValidationError
import ar.edu.unaj.fixit.core.domain.usecase.CreateRequestUseCase
import ar.edu.unaj.fixit.core.domain.usecase.GetCurrentPlaceUseCase
import ar.edu.unaj.fixit.core.ui.components.SamplePhotos
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * "Nueva solicitud" form. The coordinates ([point]) only come from "Usar mi ubicación"; the
 * [address] text is editable. [validationError] is shown next to its section; any other error is a
 * one-shot [message] cleared with `onMessageShown()`. [createdRequestId] is a one-shot navigation
 * event cleared with `onNavigated()`.
 */
data class CreateRequestUiState(
    val category: ServiceCategory? = null,
    val description: String = "",
    val photoUris: List<String> = emptyList(),
    val point: GeoPoint? = null,
    val address: String = "",
    val isLocating: Boolean = false,
    val isSubmitting: Boolean = false,
    val validationError: ValidationError? = null,
    val message: DomainError? = null,
    val createdRequestId: String? = null,
) {
    val canAddPhoto: Boolean get() = photoUris.size < Limits.PHOTOS_MAX

    /** Without coordinates there is no place, so the use case answers LocationRequired. */
    fun toNewRequest(): NewRequest = NewRequest(
        category = category,
        description = description,
        photoUris = photoUris,
        place = point?.let { Place(it, address) },
    )
}

@HiltViewModel
class CreateRequestViewModel @Inject constructor(
    private val createRequest: CreateRequestUseCase,
    private val getCurrentPlace: GetCurrentPlaceUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CreateRequestUiState())
    val uiState: StateFlow<CreateRequestUiState> = _uiState.asStateFlow()

    /** Single selection: the tapped category replaces the previous one. */
    fun onCategorySelect(category: ServiceCategory) = _uiState.update { it.copy(category = category) }

    fun onDescriptionChange(description: String) = _uiState.update { it.copy(description = description) }

    /** Phase 1: adds a sample photo not used yet (there are as many samples as the photo limit). */
    fun onAddSamplePhoto() = _uiState.update {
        if (!it.canAddPhoto) return@update it
        val sample = SamplePhotos.firstOrNull { uri -> uri !in it.photoUris } ?: SamplePhotos.first()
        it.copy(photoUris = it.photoUris + sample)
    }

    fun onRemovePhoto(index: Int) = _uiState.update {
        if (index !in it.photoUris.indices) it else it.copy(photoUris = it.photoUris.filterIndexed { i, _ -> i != index })
    }

    fun onUseLocation() {
        if (_uiState.value.isLocating) return
        _uiState.update { it.copy(isLocating = true) }
        viewModelScope.launch {
            val result = getCurrentPlace()
            _uiState.update {
                when (result) {
                    is AppResult.Success -> it.copy(isLocating = false, point = result.data.point, address = result.data.address)
                    // The address stays editable; without coordinates publishing answers LocationRequired.
                    is AppResult.Error -> it.copy(isLocating = false, message = result.error)
                }
            }
        }
    }

    fun onAddressChange(address: String) = _uiState.update { it.copy(address = address) }

    fun onSubmit() {
        val form = _uiState.value
        if (form.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, validationError = null) }
        viewModelScope.launch {
            val result = createRequest(form.toNewRequest())
            _uiState.update {
                when (result) {
                    is AppResult.Success -> it.copy(isSubmitting = false, createdRequestId = result.data.id)
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

    fun onNavigated() = _uiState.update { it.copy(createdRequestId = null) }
}
