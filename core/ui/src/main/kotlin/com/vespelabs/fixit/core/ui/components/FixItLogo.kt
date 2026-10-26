package com.vespelabs.fixit.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vespelabs.fixit.core.ui.R
import com.vespelabs.fixit.core.ui.theme.FixItTheme

/**
 * The FixIt logo: the same mark and teal the launcher icon uses, on a rounded square. Its colours
 * are fixed, not taken from the scheme, so the logo looks the same in light and dark themes.
 *
 * [contentDescription] is null by default (decorative): pass the app name where nothing else on
 * screen names it. The brand is not a string resource, as in [FixItWordmark].
 */
@Composable
fun FixItLogo(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.25f))
            .background(colorResource(R.color.fixit_logo_background)),
    ) {
        // The 108dp mark is drawn larger than the box, as a launcher does with an adaptive icon's
        // foreground: only its inner 72dp is meant to be visible.
        Image(
            painter = painterResource(R.drawable.fixit_logo_mark),
            contentDescription = contentDescription,
            modifier = Modifier.requiredSize(size * 1.5f).align(Alignment.Center),
        )
    }
}

@Preview
@Composable
private fun FixItLogoPreview() {
    FixItTheme { FixItLogo() }
}
