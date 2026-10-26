package com.vespelabs.fixit.feature.client.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Limits
import com.vespelabs.fixit.core.domain.model.NewRequest
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.model.ServiceCategory
import com.vespelabs.fixit.core.domain.repository.AuthRepository
import com.vespelabs.fixit.core.domain.repository.UserRepository
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.domain.result.ValidationError
import com.vespelabs.fixit.core.domain.usecase.CreateRequestUseCase
import com.vespelabs.fixit.core.domain.usecase.GetCurrentPlaceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * "Nueva solicitud" form. The coordinates ([point]) come from "Usar mi ubicación", from "Elegir en
 * el mapa" or, when the form opens, from the client area; they are never typed. The [address] text
 * is editable. [validationError] is shown next to its section; any other error is a
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
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CreateRequestUiState())
    val uiState: StateFlow<CreateRequestUiState> = _uiState.asStateFlow()

    init {
        preloadArea()
    }

    /**
     * A single read of the client area (repair-requests spec). It never overwrites a location the
     * client already set, and a failed read leaves the form empty without a message.
     */
    private fun preloadArea() {
        viewModelScope.launch {
            val userId = authRepository.currentUserId() ?: return@launch
            val area = (userRepository.get(userId) as? AppResult.Success)?.data?.area ?: return@launch
            _uiState.update { if (it.point != null) it else it.copy(point = area.point, address = area.address) }
        }
    }

    /** Single selection: the tapped category replaces the previous one. */
    fun onCategorySelect(category: ServiceCategory) = _uiState.update { it.copy(category = category) }

    fun onDescriptionChange(description: String) = _uiState.update { it.copy(description = description) }

    /**
     * Appends the URIs returned by the gallery or the camera, keeping at most [Limits.PHOTOS_MAX].
     * An empty list (picker or capture cancelled) leaves the photos unchanged.
     */
    fun onPhotosAdded(uris: List<String>) = _uiState.update {
        val missing = Limits.PHOTOS_MAX - it.photoUris.size
        if (uris.isEmpty() || missing <= 0) it else it.copy(photoUris = it.photoUris + uris.take(missing))
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

    /** The place confirmed in "Elegir en el mapa": coordinates and address are replaced together. */
    fun onPlacePicked(place: Place) = _uiState.update { it.copy(point = place.point, address = place.address) }

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
