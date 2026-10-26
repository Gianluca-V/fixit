package com.vespelabs.fixit.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vespelabs.fixit.core.ui.R

/**
 * Manrope, bundled as one variable font (OFL licence, see res/font).
 *
 * Why this typeface for a home-repair app:
 *  - High x-height and open apertures: it stays legible at 11-13sp, which is where labels and
 *    badges live on a phone held at arm's length in the street.
 *  - Semi-geometric skeleton with a few distinctive shapes (the single-storey "a", the flat "r"):
 *    recognisable, but neutral enough that it never competes with the content.
 *  - It is one of the most used UI faces on mobile, so it reads as "product", not "template".
 *
 * One 160 KB variable file covers every weight, instead of four static files. The weight axis is
 * applied through [FontVariation], which the platform supports from API 26 (the app's minSdk).
 */
private fun manrope(weight: Int): Font = Font(
    resId = R.font.manrope_variable,
    weight = FontWeight(weight),
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val ManropeFamily = FontFamily(
    manrope(400),
    manrope(500),
    manrope(600),
    manrope(700),
    manrope(800),
)

/**
 * Mobile scale, slightly tighter than the Material 3 defaults: the app is dense (lists, cards,
 * status badges), so line heights stay close to the type size and headings do not waste vertical
 * space. Letter spacing is negative on large sizes and slightly positive on small ones, which is
 * what keeps both ends legible.
 */
private fun textStyle(
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    letterSpacing: Double = 0.0,
): TextStyle = TextStyle(
    fontFamily = ManropeFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

val FixItTypography = Typography(
    displayLarge = textStyle(FontWeight.Bold, 44, 52, letterSpacing = -0.5),
    displayMedium = textStyle(FontWeight.Bold, 38, 46, letterSpacing = -0.5),
    displaySmall = textStyle(FontWeight.Bold, 32, 40, letterSpacing = -0.25),
    headlineLarge = textStyle(FontWeight.Bold, 30, 38, letterSpacing = -0.25),
    headlineMedium = textStyle(FontWeight.SemiBold, 26, 34),
    headlineSmall = textStyle(FontWeight.SemiBold, 23, 31),
    titleLarge = textStyle(FontWeight.SemiBold, 21, 28),
    titleMedium = textStyle(FontWeight.SemiBold, 17, 24, letterSpacing = 0.1),
    titleSmall = textStyle(FontWeight.SemiBold, 15, 21, letterSpacing = 0.1),
    bodyLarge = textStyle(FontWeight.Normal, 16, 24, letterSpacing = 0.15),
    bodyMedium = textStyle(FontWeight.Normal, 14, 21, letterSpacing = 0.2),
    bodySmall = textStyle(FontWeight.Normal, 12, 18, letterSpacing = 0.3),
    labelLarge = textStyle(FontWeight.SemiBold, 15, 20, letterSpacing = 0.1),
    labelMedium = textStyle(FontWeight.Medium, 13, 18, letterSpacing = 0.4),
    labelSmall = textStyle(FontWeight.Medium, 11, 15, letterSpacing = 0.5),
)
