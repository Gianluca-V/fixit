package com.vespelabs.fixit.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.vespelabs.fixit.core.domain.model.Limits
import com.vespelabs.fixit.core.ui.R
import com.vespelabs.fixit.core.ui.theme.FixItTheme

/** Five stars. Read-only when [onRatingChange] is null; [rating] 0 means "not rated yet". */
@Composable
fun RatingBar(
    rating: Int,
    modifier: Modifier = Modifier,
    onRatingChange: ((Int) -> Unit)? = null,
) {
    val description = stringResource(R.string.rating_value, rating)
    val rowModifier = if (onRatingChange == null) {
        modifier.semantics { contentDescription = description }
    } else {
        modifier
    }
    Row(modifier = rowModifier) {
        (Limits.RATING_MIN..Limits.RATING_MAX).forEach { value ->
            val filled = value <= rating
            val icon = if (filled) Icons.Filled.Star else Icons.Outlined.Star
            val tint = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            if (onRatingChange == null) {
                Icon(icon, contentDescription = null, tint = tint)
            } else {
                IconButton(onClick = { onRatingChange(value) }) {
                    Icon(icon, contentDescription = stringResource(R.string.rating_star, value), tint = tint)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RatingBarPreview() {
    FixItTheme {
        Column {
            RatingBar(rating = 4)
            RatingBar(rating = 2, onRatingChange = {})
        }
    }
}
