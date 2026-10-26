package com.vespelabs.fixit.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * FixIt palette, "Oficio".
 *
 * The domain is home repair: a client lets a stranger into their house and a professional sells
 * competence. The palette answers that with three ideas.
 *
 *  - Deep teal (petrol) is the brand. It reads as trust and calm, and it is deliberately not the
 *    corporate blue every services app uses.
 *  - Safety amber is the accent: the colour of a hi-vis vest and a hard hat. It marks action, and
 *    it is the only warm note in an otherwise cool palette, so it draws the eye where it matters.
 *  - Warm paper neutrals replace pure white: a freshly plastered wall, not a clinic.
 *
 * Dynamic colour is opt-in (see [com.vespelabs.fixit.core.ui.theme.FixItTheme]): a brand that takes
 * the colour of the user's wallpaper is not recognisable.
 */

// Light tonal values. Every text colour is at least 4.5:1 against the surface it sits on.
private val Teal10 = Color(0xFF00201F)
private val Teal30 = Color(0xFF0B4F51)
private val Teal40 = Color(0xFF0B5F62)
private val Teal80 = Color(0xFF7ED5D3)
private val Teal90 = Color(0xFFB4E9E7)

private val Amber10 = Color(0xFF261A00)
private val Amber30 = Color(0xFF5C4300)
private val Amber40 = Color(0xFF7A5900)
private val Amber80 = Color(0xFFF5BF57)
private val Amber90 = Color(0xFFFFDF9E)

private val Clay10 = Color(0xFF3A0B00)
private val Clay30 = Color(0xFF7A2E11)
private val Clay40 = Color(0xFF9A4520)
private val Clay80 = Color(0xFFFFB59B)
private val Clay90 = Color(0xFFFFDBCD)

// Warm neutral ramp: paper -> charcoal. Kept slightly yellow so the UI never looks clinical.
private val Paper99 = Color(0xFFFCF9F3)
private val Paper98 = Color(0xFFF6F3ED)
private val Paper96 = Color(0xFFF0EEE7)
private val Paper94 = Color(0xFFEBE8E1)
private val Paper92 = Color(0xFFE5E2DB)
private val Paper88 = Color(0xFFDCD9D3)
private val Ink10 = Color(0xFF1B1C19)
private val Ink30 = Color(0xFF45483F)
private val Ink50 = Color(0xFF6F7977)

// Dark ramp: warm charcoal instead of pure black, so the app is comfortable at night.
private val Night6 = Color(0xFF131311)
private val Night12 = Color(0xFF1E1F1C)
private val Night17 = Color(0xFF282925)
private val Night22 = Color(0xFF333430)
private val Night26 = Color(0xFF3E3F3A)
private val Night30 = Color(0xFF45443B)
private val Night80 = Color(0xFFCAC5B8)
private val Night85 = Color(0xFFE5E2DB)

private val Brick40 = Color(0xFFB3261E) // error: a warning sign, not an alarm button
private val Brick80 = Color(0xFFFFB4AB)
private val Brick90 = Color(0xFFFFDAD6)
private val Brick20 = Color(0xFF690005)
private val Brick10 = Color(0xFF410E0B)
private val Brick95 = Color(0xFF93000A)

internal val LightColors = lightColorScheme(
    primary = Teal40,
    onPrimary = Color.White,
    primaryContainer = Teal90,
    onPrimaryContainer = Teal10,
    inversePrimary = Teal80,
    secondary = Amber40,
    onSecondary = Color.White,
    secondaryContainer = Amber90,
    onSecondaryContainer = Amber10,
    tertiary = Clay40,
    onTertiary = Color.White,
    tertiaryContainer = Clay90,
    onTertiaryContainer = Clay10,
    background = Paper99,
    onBackground = Ink10,
    surface = Paper99,
    onSurface = Ink10,
    surfaceVariant = Paper92,
    onSurfaceVariant = Ink30,
    surfaceTint = Teal40,
    surfaceBright = Paper99,
    surfaceDim = Paper88,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Paper98,
    surfaceContainer = Paper96,
    surfaceContainerHigh = Paper94,
    surfaceContainerHighest = Paper92,
    inverseSurface = Ink10,
    inverseOnSurface = Paper98,
    error = Brick40,
    onError = Color.White,
    errorContainer = Brick90,
    onErrorContainer = Brick10,
    outline = Ink50,
    outlineVariant = Paper88,
    scrim = Color.Black,
)

internal val DarkColors = darkColorScheme(
    primary = Teal80,
    onPrimary = Teal10,
    primaryContainer = Teal30,
    onPrimaryContainer = Teal90,
    inversePrimary = Teal40,
    secondary = Amber80,
    onSecondary = Amber10,
    secondaryContainer = Amber30,
    onSecondaryContainer = Amber90,
    tertiary = Clay80,
    onTertiary = Clay10,
    tertiaryContainer = Clay30,
    onTertiaryContainer = Clay90,
    background = Night6,
    onBackground = Night85,
    surface = Night6,
    onSurface = Night85,
    surfaceVariant = Night30,
    onSurfaceVariant = Night80,
    surfaceTint = Teal80,
    surfaceBright = Night22,
    surfaceDim = Night6,
    surfaceContainerLowest = Color(0xFF0C0D0B),
    surfaceContainerLow = Night12,
    surfaceContainer = Night17,
    surfaceContainerHigh = Night22,
    surfaceContainerHighest = Night26,
    inverseSurface = Night85,
    inverseOnSurface = Night6,
    error = Brick80,
    onError = Brick20,
    errorContainer = Brick95,
    onErrorContainer = Brick90,
    outline = Night80,
    outlineVariant = Night30,
    scrim = Color.Black,
)

// ---------------------------------------------------------------------------------------------
// Extra roles. Material 3 has no slot for "this went well" or "this is a rating", and forcing them
// into primary/tertiary makes both lose meaning. These four live outside the scheme and travel
// through [LocalFixItColors], so a theme change updates them together with the rest.
// ---------------------------------------------------------------------------------------------

/** Green for finished work: the one colour in the app that says "done, nothing to do here". */
private val Leaf20 = Color(0xFF0B2A12)
private val Leaf30 = Color(0xFF2B6136)
private val Leaf40 = Color(0xFF2E6B3C)
private val Leaf80 = Color(0xFF9BD3A2)
private val Leaf90 = Color(0xFFC9EBC9)

/** Stars. Amber, not teal: a rating is praise, and praise should not look like a button. */
private val Star40 = Color(0xFFB26B00)
private val Star80 = Color(0xFFFFC772)

@Immutable
data class FixItColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val rating: Color,
)

internal val LightExtraColors = FixItColors(
    success = Leaf40,
    onSuccess = Color.White,
    successContainer = Leaf90,
    onSuccessContainer = Leaf20,
    rating = Star40,
)

internal val DarkExtraColors = FixItColors(
    success = Leaf80,
    onSuccess = Leaf20,
    successContainer = Leaf30,
    onSuccessContainer = Leaf90,
    rating = Star80,
)

internal val LocalFixItColors = staticCompositionLocalOf { LightExtraColors }
