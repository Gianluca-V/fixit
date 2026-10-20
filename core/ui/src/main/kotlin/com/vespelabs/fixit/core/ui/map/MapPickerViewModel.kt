package com.vespelabs.fixit.core.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.repository.AddressResolver
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.usecase.SearchAddressUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the picker opens when the form has no location yet (address-search spec). */
val DEFAULT_MAP_CENTER = GeoPoint(-34.8065, -58.2788)

/** Quiet time after the last keystroke before searching (address-search spec). */
const val SEARCH_DEBOUNCE_MS = 300L

/** Two camera positions closer than this (in degrees, about 1 m) are the same point. */
private const val SAME_POINT_DEGREES = 1e-5

/**
 * The chosen point is always the camera [center] (the pin is fixed in the middle). [cameraTarget] is a one-shot
 * request to move the camera to a chosen suggestion, cleared with `onCameraMoved()`.
 */
data class MapPickerUiState(
    val center: GeoPoint,
    val address: String,
    val isResolving: Boolean = false,
    val query: String = "",
    val suggestions: List<Place> = emptyList(),
    val searchUnavailable: Boolean = false,
    val cameraTarget: GeoPoint? = null,
)

/**
 * Map picker logic (icon-map-address D4). It never touches the GPS, so it never asks for the location permission:
 * the address of a point comes from [AddressResolver] and suggestions from [SearchAddressUseCase].
 */
@HiltViewModel(assistedFactory = MapPickerViewModel.Factory::class)
class MapPickerViewModel @AssistedInject constructor(
    @Assisted initial: Place?,
    private val searchAddress: SearchAddressUseCase,
    private val addressResolver: AddressResolver,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(initial: Place?): MapPickerViewModel
    }

    private val _uiState = MutableStateFlow(
        MapPickerUiState(center = initial?.point ?: DEFAULT_MAP_CENTER, address = initial?.address.orEmpty()),
    )
    val uiState: StateFlow<MapPickerUiState> = _uiState.asStateFlow()

    private val queries = MutableStateFlow("")
    private var resolveJob: Job? = null

    /** A camera stop at this point was caused by the app (opening or a suggestion): its address is already known. */
    private var expectedStop: GeoPoint? = initial?.point

    init {
        // collectLatest cancels the wait and any search in flight on every keystroke: that is both the 300 ms debounce
        // and the guarantee that an older text never shows its suggestions.
        viewModelScope.launch {
            queries.collectLatest { text ->
                delay(SEARCH_DEBOUNCE_MS)
                when (val result = searchAddress(text, _uiState.value.center)) {
                    is AppResult.Success -> _uiState.update { it.copy(suggestions = result.data, searchUnavailable = false) }
                    is AppResult.Error -> _uiState.update { it.copy(suggestions = emptyList(), searchUnavailable = true) }
                }
            }
        }
    }

    fun onQueryChange(text: String) {
        // Old suggestions never stay next to a new text; an empty text hides them at once.
        _uiState.update { it.copy(query = text, suggestions = emptyList(), searchUnavailable = false) }
        queries.value = text
    }

    fun onSuggestionClick(place: Place) {
        resolveJob?.cancel()
        expectedStop = place.point
        _uiState.update {
            it.copy(
                center = place.point,
                address = place.address,
                isResolving = false,
                query = "",
                suggestions = emptyList(),
                searchUnavailable = false,
                cameraTarget = place.point,
            )
        }
        queries.value = ""
    }

    fun onCameraMoved() = _uiState.update { it.copy(cameraTarget = null) }

    /** The map stopped at [point]: it becomes the chosen point and, unless the app moved it there, its address is resolved. */
    fun onCameraIdle(point: GeoPoint) {
        val expected = expectedStop
        expectedStop = null
        if (expected != null && expected.isNear(point)) return

        resolveJob?.cancel()
        _uiState.update { it.copy(center = point, isResolving = true) }
        resolveJob = viewModelScope.launch {
            val address = when (val result = addressResolver.resolve(point)) {
                is AppResult.Success -> result.data
                is AppResult.Error -> "" // stays editable (location spec)
            }
            _uiState.update { it.copy(address = address, isResolving = false) }
        }
    }

    /** What the user types wins over a resolution still in progress. */
    fun onAddressChange(text: String) {
        resolveJob?.cancel()
        _uiState.update { it.copy(address = text, isResolving = false) }
    }

    /** The place handed back to the form on "Confirmar"; the form validates it. */
    fun confirmedPlace(): Place = _uiState.value.let { Place(it.center, it.address.trim()) }

    private fun GeoPoint.isNear(other: GeoPoint): Boolean =
        abs(latitude - other.latitude) < SAME_POINT_DEGREES && abs(longitude - other.longitude) < SAME_POINT_DEGREES
}
