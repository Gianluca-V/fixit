package com.vespelabs.fixit.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.model.ProfileForm
import com.vespelabs.fixit.core.domain.model.ServiceCategory
import com.vespelabs.fixit.core.domain.model.User
import com.vespelabs.fixit.core.domain.result.FormField
import com.vespelabs.fixit.core.domain.result.ValidationError
import com.vespelabs.fixit.core.ui.R
import com.vespelabs.fixit.core.ui.text.asMessage
import com.vespelabs.fixit.core.ui.theme.FixItTheme

/**
 * What the user typed in the profile fields. [radiusKm] is text so the user can type freely. [area] is the optional
 * client area; the use case ignores it for a professional.
 */
data class ProfileFormState(
    val name: String = "",
    val phone: String = "",
    val categories: Set<ServiceCategory> = emptySet(),
    val base: Place? = null,
    val radiusKm: String = "",
    val area: Place? = null,
) {
    /** Input for the use cases; a radius that is not a number becomes null and fails validation. */
    fun toProfileForm(): ProfileForm = ProfileForm(
        name = name,
        phone = phone,
        categories = categories,
        base = base,
        radiusKm = radiusKm.trim().toIntOrNull(),
        area = area,
    )

    companion object {
        /** Preloads the form with the current values of [user]. */
        fun from(user: User): ProfileFormState = ProfileFormState(
            name = user.name,
            phone = user.phone,
            categories = user.professional?.categories.orEmpty(),
            base = user.professional?.base,
            radiusKm = user.professional?.radiusKm?.toString().orEmpty(),
            area = user.area,
        )
    }
}

/**
 * Shared by sign-up and both "edit profile" screens. Professional fields (categories, base point,
 * radius) are shown only when [showProfessionalFields]; the client area only when [showClientArea].
 * "Usar mi ubicación" and "Elegir en el mapa" appear only when their callbacks are given, so sign-up
 * keeps showing only the first one. The error is shown next to the field it belongs to
 * ([ValidationError.field]).
 */
@Composable
fun ProfileFormFields(
    state: ProfileFormState,
    onStateChange: (ProfileFormState) -> Unit,
    modifier: Modifier = Modifier,
    showProfessionalFields: Boolean = false,
    validationError: ValidationError? = null,
    enabled: Boolean = true,
    onUseLocationClick: (() -> Unit)? = null,
    onPickOnMapClick: (() -> Unit)? = null,
    showClientArea: Boolean = false,
) {
    fun errorFor(vararg fields: FormField): ValidationError? =
        validationError?.takeIf { it.field in fields }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextFieldWithError(
            value = state.name,
            onValueChange = { onStateChange(state.copy(name = it)) },
            label = stringResource(R.string.profile_field_name),
            error = errorFor(FormField.NAME),
            enabled = enabled,
        )
        TextFieldWithError(
            value = state.phone,
            onValueChange = { onStateChange(state.copy(phone = it)) },
            label = stringResource(R.string.profile_field_phone),
            error = errorFor(FormField.PHONE),
            enabled = enabled,
            keyboardType = KeyboardType.Phone,
        )
        if (showProfessionalFields) {
            Text(stringResource(R.string.profile_field_categories), style = MaterialTheme.typography.labelLarge)
            CategoryChips(
                selected = state.categories,
                onCategoryClick = { category ->
                    val updated = if (category in state.categories) state.categories - category else state.categories + category
                    onStateChange(state.copy(categories = updated))
                },
                enabled = enabled,
            )
            ErrorText(errorFor(FormField.CATEGORIES))

            Text(stringResource(R.string.profile_field_base), style = MaterialTheme.typography.labelLarge)
            Text(
                text = state.base?.address?.ifBlank { null } ?: stringResource(R.string.profile_field_base_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
            LocationButtons(onUseLocationClick, onPickOnMapClick, enabled)
            ErrorText(errorFor(FormField.BASE, FormField.LOCATION))

            TextFieldWithError(
                value = state.radiusKm,
                onValueChange = { onStateChange(state.copy(radiusKm = it)) },
                label = stringResource(R.string.profile_field_radius),
                error = errorFor(FormField.RADIUS),
                enabled = enabled,
                keyboardType = KeyboardType.Number,
            )
        }
        if (showClientArea) {
            ClientAreaFields(
                area = state.area,
                onAreaChange = { onStateChange(state.copy(area = it)) },
                error = errorFor(FormField.LOCATION),
                enabled = enabled,
                onUseLocationClick = onUseLocationClick,
                onPickOnMapClick = onPickOnMapClick,
            )
        }
    }
}

/** Optional client area: its address is editable once there is one, and "Quitar zona" clears it. */
@Composable
private fun ClientAreaFields(
    area: Place?,
    onAreaChange: (Place?) -> Unit,
    error: ValidationError?,
    enabled: Boolean,
    onUseLocationClick: (() -> Unit)?,
    onPickOnMapClick: (() -> Unit)?,
) {
    Text(stringResource(R.string.profile_field_area), style = MaterialTheme.typography.labelLarge)
    if (area == null) {
        Text(stringResource(R.string.profile_area_empty), style = MaterialTheme.typography.bodyMedium)
    } else {
        TextFieldWithError(
            value = area.address,
            onValueChange = { onAreaChange(area.copy(address = it)) },
            label = stringResource(R.string.map_address_label),
            error = error,
            enabled = enabled,
        )
    }
    Text(stringResource(R.string.profile_area_visibility), style = MaterialTheme.typography.bodySmall)
    LocationButtons(
        onUseLocationClick = onUseLocationClick,
        onPickOnMapClick = onPickOnMapClick,
        enabled = enabled,
        onClearClick = if (area != null) ({ onAreaChange(null) }) else null,
    )
    if (area == null) ErrorText(error)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocationButtons(
    onUseLocationClick: (() -> Unit)?,
    onPickOnMapClick: (() -> Unit)?,
    enabled: Boolean,
    onClearClick: (() -> Unit)? = null,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onUseLocationClick != null) {
            OutlinedButton(onClick = onUseLocationClick, enabled = enabled) {
                Text(stringResource(R.string.profile_use_location))
            }
        }
        if (onPickOnMapClick != null) {
            OutlinedButton(onClick = onPickOnMapClick, enabled = enabled) {
                Text(stringResource(R.string.map_pick_on_map))
            }
        }
        if (onClearClick != null) {
            OutlinedButton(onClick = onClearClick, enabled = enabled) {
                Text(stringResource(R.string.profile_area_clear))
            }
        }
    }
}

@Composable
private fun TextFieldWithError(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: ValidationError?,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it.asMessage()) } },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ErrorText(error: ValidationError?) {
    if (error != null) {
        Text(error.asMessage(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileFormFieldsPreview() {
    FixItTheme {
        ProfileFormFields(
            state = ProfileFormState(
                name = "Carlos Gómez",
                phone = "11 5555-0101",
                categories = setOf(ServiceCategory.ELECTRICITY),
                base = Place(GeoPoint(-34.8065, -58.2788), "Florencio Varela"),
                radiusKm = "10",
            ),
            onStateChange = {},
            showProfessionalFields = true,
            validationError = ValidationError.INVALID_PHONE,
            onUseLocationClick = {},
            onPickOnMapClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileFormFieldsClientAreaPreview() {
    FixItTheme {
        ProfileFormFields(
            state = ProfileFormState(
                name = "Ana López",
                phone = "11 5555-0001",
                area = Place(GeoPoint(-34.7206, -58.2546), "Calle 123, Quilmes"),
            ),
            onStateChange = {},
            showClientArea = true,
            onUseLocationClick = {},
            onPickOnMapClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileFormFieldsClientWithoutAreaPreview() {
    FixItTheme {
        ProfileFormFields(
            state = ProfileFormState(name = "Bruno Díaz", phone = "11 5555-0002"),
            onStateChange = {},
            showClientArea = true,
            onUseLocationClick = {},
            onPickOnMapClick = {},
        )
    }
}
