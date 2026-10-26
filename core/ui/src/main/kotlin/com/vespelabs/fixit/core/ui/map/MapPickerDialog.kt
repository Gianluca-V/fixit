package com.vespelabs.fixit.core.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.ui.R
import com.vespelabs.fixit.core.ui.components.ScreenScaffold
import com.vespelabs.fixit.core.ui.theme.FixItTheme
import java.util.UUID

private val PIN_SIZE = 48.dp

/**
 * Full-screen "Elegir en el mapa" (icon-map-address D4). The screen that shows it keeps whether it is open;
 * [onConfirm] receives the chosen place, while [onDismiss] (Cancelar or back) leaves the form untouched.
 * Each opening gets its own ViewModel (a new key), so it starts from [initial]; a rotation keeps it.
 */
@Composable
fun MapPickerDialog(
    initial: Place?,
    onConfirm: (Place) -> Unit,
    onDismiss: () -> Unit,
) {
    val openingId = rememberSaveable { UUID.randomUUID().toString() }
    val viewModel = hiltViewModel<MapPickerViewModel, MapPickerViewModel.Factory>(key = "map-picker-$openingId") {
        it.create(initial)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        MapPickerScreen(
            uiState = uiState,
            map = { modifier ->
                MapLibreMap(
                    initialCenter = uiState.center,
                    cameraTarget = uiState.cameraTarget,
                    onCameraIdle = viewModel::onCameraIdle,
                    onCameraMoved = viewModel::onCameraMoved,
                    modifier = modifier,
                )
            },
            onQueryChange = viewModel::onQueryChange,
            onSuggestionClick = viewModel::onSuggestionClick,
            onAddressChange = viewModel::onAddressChange,
            onConfirm = { onConfirm(viewModel.confirmedPlace()) },
            onDismiss = onDismiss,
        )
    }
}

/** Stateless picker: search with suggestions over the map, the fixed pin in the middle, the address and the actions. */
@Composable
fun MapPickerScreen(
    uiState: MapPickerUiState,
    map: @Composable (Modifier) -> Unit,
    onQueryChange: (String) -> Unit,
    onSuggestionClick: (Place) -> Unit,
    onAddressChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(title = stringResource(R.string.map_title), modifier = modifier, onBack = onDismiss) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(uiState.query, onQueryChange, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            if (uiState.searchUnavailable) {
                Text(
                    text = stringResource(R.string.map_search_unavailable),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                map(Modifier.fillMaxSize())
                // The tip of the pin marks the camera center, which is the chosen point.
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = stringResource(R.string.map_pin_description),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center).offset(y = -PIN_SIZE / 2).size(PIN_SIZE),
                )
                if (uiState.suggestions.isNotEmpty()) {
                    Suggestions(uiState.suggestions, onSuggestionClick, Modifier.align(Alignment.TopCenter))
                }
            }
            AddressSection(uiState, onAddressChange, onConfirm, onDismiss)
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.map_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.common_cancel))
                }
            }
        },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun Suggestions(places: List<Place>, onClick: (Place) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp), shadowElevation = 4.dp, tonalElevation = 2.dp) {
        LazyColumn {
            items(places) { place ->
                Text(
                    text = place.address,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().clickable { onClick(place) }.padding(16.dp),
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun AddressSection(
    uiState: MapPickerUiState,
    onAddressChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.map_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = uiState.address,
            onValueChange = onAddressChange,
            label = { Text(stringResource(R.string.map_address_label)) },
            supportingText = if (uiState.isResolving) {
                { Text(stringResource(R.string.map_resolving)) }
            } else {
                null
            },
            trailingIcon = if (uiState.isResolving) {
                { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.common_cancel))
            }
            Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.map_confirm))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MapPickerScreenSuggestionsPreview() {
    FixItTheme {
        MapPickerScreen(
            uiState = MapPickerUiState(
                center = DEFAULT_MAP_CENTER,
                address = "Av. San Martín 2000, Florencio Varela",
                query = "Calle 12",
                suggestions = listOf(
                    Place(GeoPoint(-34.7631, -58.2112), "Calle 12 450, Berazategui"),
                    Place(GeoPoint(-34.7300, -58.2700), "Calle 123, Quilmes"),
                ),
            ),
            map = { MapLibreMap(DEFAULT_MAP_CENTER, null, {}, {}, it) },
            onQueryChange = {},
            onSuggestionClick = {},
            onAddressChange = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MapPickerScreenUnavailablePreview() {
    FixItTheme {
        MapPickerScreen(
            uiState = MapPickerUiState(
                center = DEFAULT_MAP_CENTER,
                address = "",
                isResolving = true,
                query = "San Martín",
                searchUnavailable = true,
            ),
            map = { MapLibreMap(DEFAULT_MAP_CENTER, null, {}, {}, it) },
            onQueryChange = {},
            onSuggestionClick = {},
            onAddressChange = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}
