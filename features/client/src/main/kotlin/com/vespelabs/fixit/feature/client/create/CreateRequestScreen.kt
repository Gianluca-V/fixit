package com.vespelabs.fixit.feature.client.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Limits
import com.vespelabs.fixit.core.domain.model.ServiceCategory
import com.vespelabs.fixit.core.domain.result.FormField
import com.vespelabs.fixit.core.domain.result.ValidationError
import com.vespelabs.fixit.core.ui.components.CategoryChips
import com.vespelabs.fixit.core.ui.components.PhotoPickerButtons
import com.vespelabs.fixit.core.ui.components.PhotoThumbnails
import com.vespelabs.fixit.core.ui.components.SamplePhotos
import com.vespelabs.fixit.core.ui.text.asMessage
import com.vespelabs.fixit.core.ui.theme.FixItTheme
import com.vespelabs.fixit.feature.client.R
import com.vespelabs.fixit.feature.client.common.FieldErrorText
import com.vespelabs.fixit.feature.client.common.MessageScaffold

/** "Nueva solicitud". `:app` resolves onCreated(id) to goBackFrom(CreateRequestKey) + navigate(detail). */
@Composable
fun CreateRequestRoute(
    onBack: () -> Unit,
    onCreated: (requestId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateRequestViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val createdRequestId = uiState.createdRequestId
    LaunchedEffect(createdRequestId) {
        if (createdRequestId != null) {
            onCreated(createdRequestId)
            viewModel.onNavigated()
        }
    }
    CreateRequestScreen(
        uiState = uiState,
        onBack = onBack,
        onCategorySelect = viewModel::onCategorySelect,
        onDescriptionChange = viewModel::onDescriptionChange,
        onPhotosAdded = viewModel::onPhotosAdded,
        onRemovePhoto = viewModel::onRemovePhoto,
        onUseLocation = viewModel::onUseLocation,
        onAddressChange = viewModel::onAddressChange,
        onSubmit = viewModel::onSubmit,
        onMessageShown = viewModel::onMessageShown,
        modifier = modifier,
    )
}

@Composable
fun CreateRequestScreen(
    uiState: CreateRequestUiState,
    onBack: () -> Unit,
    onCategorySelect: (ServiceCategory) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPhotosAdded: (List<String>) -> Unit,
    onRemovePhoto: (index: Int) -> Unit,
    onUseLocation: () -> Unit,
    onAddressChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = !uiState.isSubmitting
    fun errorFor(field: FormField) = uiState.validationError?.takeIf { it.field == field }

    MessageScaffold(
        title = stringResource(R.string.client_create_request_title),
        message = uiState.message,
        onMessageShown = onMessageShown,
        modifier = modifier,
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.client_create_category))
            CategoryChips(
                selected = setOfNotNull(uiState.category),
                onCategoryClick = onCategorySelect,
                enabled = enabled,
            )
            FieldErrorText(errorFor(FormField.CATEGORY))

            DescriptionField(
                value = uiState.description,
                onValueChange = onDescriptionChange,
                error = errorFor(FormField.DESCRIPTION),
                enabled = enabled,
            )

            SectionTitle(stringResource(R.string.client_create_photos))
            PhotoThumbnails(photoUrls = uiState.photoUris, onRemove = if (enabled) onRemovePhoto else null)
            PhotoPickerButtons(
                photoCount = uiState.photoUris.size,
                maxPhotos = Limits.PHOTOS_MAX,
                onPhotosPicked = onPhotosAdded,
                enabled = enabled,
            )
            FieldErrorText(errorFor(FormField.PHOTOS))

            SectionTitle(stringResource(R.string.client_create_location))
            OutlinedButton(onClick = onUseLocation, enabled = enabled && !uiState.isLocating) {
                if (uiState.isLocating) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.client_create_use_location))
                }
            }
            // The address is always editable; the coordinates only come from "Usar mi ubicación".
            OutlinedTextField(
                value = uiState.address,
                onValueChange = onAddressChange,
                label = { Text(stringResource(R.string.client_create_address)) },
                isError = errorFor(FormField.LOCATION) != null,
                supportingText = {
                    Text(
                        errorFor(FormField.LOCATION)?.asMessage()
                            ?: stringResource(
                                if (uiState.point == null) R.string.client_create_no_coordinates else R.string.client_create_has_coordinates,
                            ),
                    )
                },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(onClick = onSubmit, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                if (uiState.isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.client_create_submit))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge)
}

/** Description with its validation error and the `n/500` counter underneath. */
@Composable
private fun DescriptionField(
    value: String,
    onValueChange: (String) -> Unit,
    error: ValidationError?,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.client_create_description)) },
        isError = error != null,
        supportingText = {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(error?.asMessage().orEmpty(), modifier = Modifier.weight(1f))
                Text(stringResource(R.string.client_create_description_counter, value.length, Limits.DESCRIPTION_MAX))
            }
        },
        enabled = enabled,
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Preview(showBackground = true)
@Composable
private fun CreateRequestScreenPreview() {
    FixItTheme {
        CreateRequestScreen(
            uiState = CreateRequestUiState(
                category = ServiceCategory.PLUMBING,
                description = "La canilla de la cocina pierde agua.",
                photoUris = SamplePhotos.take(2),
                point = GeoPoint(-34.8065, -58.2788),
                address = "Av. San Martín 2000, Florencio Varela",
            ),
            onBack = {},
            onCategorySelect = {},
            onDescriptionChange = {},
            onPhotosAdded = {},
            onRemovePhoto = {},
            onUseLocation = {},
            onAddressChange = {},
            onSubmit = {},
            onMessageShown = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CreateRequestScreenErrorPreview() {
    FixItTheme {
        CreateRequestScreen(
            uiState = CreateRequestUiState(
                category = ServiceCategory.GAS,
                description = "Hay olor a gas en la cocina.",
                validationError = ValidationError.PHOTOS_REQUIRED,
            ),
            onBack = {},
            onCategorySelect = {},
            onDescriptionChange = {},
            onPhotosAdded = {},
            onRemovePhoto = {},
            onUseLocation = {},
            onAddressChange = {},
            onSubmit = {},
            onMessageShown = {},
        )
    }
}
