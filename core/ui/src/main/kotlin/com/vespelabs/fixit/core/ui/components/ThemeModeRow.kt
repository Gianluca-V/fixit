package com.vespelabs.fixit.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vespelabs.fixit.core.domain.model.ThemeMode
import com.vespelabs.fixit.core.ui.R
import com.vespelabs.fixit.core.ui.theme.FixItTheme
import com.vespelabs.fixit.core.ui.theme.isDark

/**
 * Dark mode switch plus a way back to "follow the device".
 *
 * The switch shows the *effective* theme, so with [ThemeMode.SYSTEM] it reflects the phone. The
 * first touch turns that into an explicit choice, which is why [ThemeMode.SYSTEM] gets its own
 * affordance instead of being a third position nobody finds: two states for the switch, one
 * escape hatch for the default.
 */
@Composable
fun ThemeModeRow(
    mode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val isDark = mode.isDark()
    val label = stringResource(R.string.settings_dark_mode)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(R.string.settings_dark_mode_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = isDark,
                onCheckedChange = { onModeChange(if (it) ThemeMode.DARK else ThemeMode.LIGHT) },
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = label },
            )
        }
        if (mode != ThemeMode.SYSTEM) {
            TextButton(
                onClick = { onModeChange(ThemeMode.SYSTEM) },
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_follow_system),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ThemeModeRowPreview() {
    FixItTheme {
        Column(Modifier.fillMaxWidth()) {
            ThemeModeRow(mode = ThemeMode.SYSTEM, onModeChange = {})
            ThemeModeRow(mode = ThemeMode.DARK, onModeChange = {})
        }
    }
}
