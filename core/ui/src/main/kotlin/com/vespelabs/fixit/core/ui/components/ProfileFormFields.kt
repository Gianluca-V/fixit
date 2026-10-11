package com.vespelabs.fixit.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

/** What the user typed in the profile fields. [radiusKm] is text so the user can type freely. */
data class ProfileFormState(
    val name: String = "",
    val phone: String = "",
    val categories: Set<ServiceCategory> = emptySet(),
    val base: Place? = null,
    val radiusKm: String = "",
) {
    /** Input for the use cases; a radius that is not a number becomes null and fails validation. */
    fun toProfileForm(): ProfileForm = ProfileForm(
        name = name,
        phone = phone,
        categories = categories,
        base = base,
        radiusKm = radiusKm.trim().toIntOrNull(),
    )

    companion object {
        /** Preloads the form with the current values of [user]. */
        fun from(user: User): ProfileFormState = ProfileFormState(
            name = user.name,
            phone = user.phone,
            categories = user.professional?.categories.orEmpty(),
            base = user.professional?.base,
            radiusKm = user.professional?.radiusKm?.toString().orEmpty(),
        )
    }
}

/**
 * Shared by sign-up and both "edit profile" screens. Professional fields (categories, base point,
 * radius) are shown only when [showProfessionalFields]. The error is shown next to the field it
 * belongs to ([ValidationError.field]).
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
            if (onUseLocationClick != null) {
                OutlinedButton(onClick = onUseLocationClick, enabled = enabled) {
                    Text(stringResource(R.string.profile_use_location))
                }
            }
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
        )
    }
}
