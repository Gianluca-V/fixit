package com.vespelabs.fixit.ui

import androidx.lifecycle.ViewModel
import com.vespelabs.fixit.core.domain.model.ThemeMode
import com.vespelabs.fixit.core.domain.repository.ThemePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * The theme of the window, read once at the composition root. It exposes the repository's
 * [StateFlow] directly: there is no state to derive, and a second copy would only risk drifting
 * from the one the profile screens write to.
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    themePreferences: ThemePreferences,
) : ViewModel() {
    val mode: StateFlow<ThemeMode> = themePreferences.mode
}
