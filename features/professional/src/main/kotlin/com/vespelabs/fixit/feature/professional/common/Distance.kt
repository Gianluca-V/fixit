package com.vespelabs.fixit.feature.professional.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.vespelabs.fixit.feature.professional.R
import java.text.NumberFormat
import java.util.Locale

/** "A 2,5 km": the domain already rounds to 0.1 km; the device language decides the separator. */
@Composable
internal fun distanceText(distanceKm: Double): String {
    val locale: Locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) {
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
    }
    return stringResource(R.string.pro_distance, format.format(distanceKm))
}
