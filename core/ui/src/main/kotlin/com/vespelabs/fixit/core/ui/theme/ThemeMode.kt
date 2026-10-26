package com.vespelabs.fixit.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.vespelabs.fixit.core.domain.model.ThemeMode

/**
 * The theme that is actually on screen. [ThemeMode.SYSTEM] reads the device setting, so the app
 * shell and the switch in the profile always agree without duplicating the rule.
 */
@Composable
@ReadOnlyComposable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
