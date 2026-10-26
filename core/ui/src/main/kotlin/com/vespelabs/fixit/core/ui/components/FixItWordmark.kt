package com.vespelabs.fixit.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.vespelabs.fixit.core.ui.theme.FixItTheme

/**
 * The FixIt wordmark: "Fix" in the text colour, "It" in the brand accent. One [Text], not two, so
 * it wraps, ellipsises and is read by TalkBack as a single word.
 *
 * The name is not a string resource: a brand is not translated. [color] and [accent] are
 * parameters because the wordmark has to work on the paper background of the sign-in screen and on
 * the tinted top bar, where the text colour is `onPrimaryContainer`.
 */
@Composable
fun FixItWordmark(
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    accent: Color = MaterialTheme.colorScheme.tertiary,
) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = color, letterSpacing = (-0.4).sp)) { append("Fix") }
            withStyle(SpanStyle(color = accent, letterSpacing = (-0.4).sp)) { append("It") }
        },
        modifier = modifier,
        style = style,
        maxLines = 1,
    )
}

@Preview(showBackground = true)
@Composable
private fun FixItWordmarkPreview() {
    FixItTheme {
        Column {
            FixItWordmark(style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            FixItWordmark()
        }
    }
}
