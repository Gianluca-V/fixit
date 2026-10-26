package com.vespelabs.fixit.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3 theme with the FixIt palette ([LightColors] and [DarkColors]), Manrope
 * ([FixItTypography]), the rounder [FixItShapes] and the extra roles in [FixItColors].
 *
 * [dynamicColor] defaults to `false` on purpose. Wallpaper colours are the fastest way to make an
 * app look like every other app on the phone: the brand would change with each user, and the
 * semantic colours (which also carry meaning: amber for urgency, green for done, teal for trust)
 * would be replaced by whatever the launcher decided. Turn it on only for a build that is
 * explicitly "system themed", and never for a marketing screenshot.
 */
@Composable
fun FixItTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(
        LocalFixItColors provides if (darkTheme) DarkExtraColors else LightExtraColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FixItTypography,
            shapes = FixItShapes,
            content = content,
        )
    }
}
