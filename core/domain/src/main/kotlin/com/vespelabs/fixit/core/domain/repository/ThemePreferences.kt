package com.vespelabs.fixit.core.domain.repository

import com.vespelabs.fixit.core.domain.model.ThemeMode
import kotlinx.coroutines.flow.StateFlow

/**
 * The appearance choice of the device owner. It is not tied to the account: the theme is a device
 * preference, so it survives signing out.
 *
 * [mode] is a [StateFlow] and not a plain [kotlinx.coroutines.flow.Flow] because the screens that
 * show the toggle need to read the current value, not react to an event. It never fails: a missing
 * or unreadable stored value reads as [ThemeMode.SYSTEM].
 */
interface ThemePreferences {
    val mode: StateFlow<ThemeMode>

    fun setMode(mode: ThemeMode)
}
