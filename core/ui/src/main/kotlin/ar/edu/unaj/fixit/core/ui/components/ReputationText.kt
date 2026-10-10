package ar.edu.unaj.fixit.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import ar.edu.unaj.fixit.core.domain.model.Reputation
import ar.edu.unaj.fixit.core.domain.rules.average
import ar.edu.unaj.fixit.core.ui.R
import ar.edu.unaj.fixit.core.ui.theme.FixItTheme
import java.text.NumberFormat
import java.util.Locale

/**
 * Average with the device language and one decimal ("4,3 de 5 (3 reseñas)"; 4.0 shows "4,0"),
 * or "Sin reseñas" when there are no reviews.
 */
@Composable
fun ReputationText(reputation: Reputation, modifier: Modifier = Modifier) {
    val average = reputation.average()
    if (average == null) {
        Text(stringResource(R.string.reputation_none), modifier, style = MaterialTheme.typography.bodyMedium)
        return
    }
    val locale: Locale = LocalConfiguration.current.locales[0]
    val formatted = remember(average, locale) {
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }.format(average)
    }
    val count = pluralStringResource(R.plurals.reputation_reviews, reputation.reviewCount, reputation.reviewCount)
    Text(
        text = stringResource(R.string.reputation_average, formatted) + " ($count)",
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Preview(showBackground = true)
@Composable
private fun ReputationTextPreview() {
    FixItTheme {
        Column {
            ReputationText(Reputation(ratingSum = 13, reviewCount = 3))
            ReputationText(Reputation())
        }
    }
}
