package com.vespelabs.fixit.core.domain.testing

import com.vespelabs.fixit.core.domain.model.ThemeMode
import com.vespelabs.fixit.core.domain.repository.ThemePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory [ThemePreferences]: every test starts at [ThemeMode.SYSTEM] unless it says otherwise. */
class TestThemePreferences(initial: ThemeMode = ThemeMode.SYSTEM) : ThemePreferences {
    private val _mode = MutableStateFlow(initial)
    override val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    /** Every mode the screen asked for, in order. */
    val writes = mutableListOf<ThemeMode>()

    override fun setMode(mode: ThemeMode) {
        writes += mode
        _mode.value = mode
    }
}
