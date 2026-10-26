package com.vespelabs.fixit.core.data.prefs

import android.content.Context
import com.vespelabs.fixit.core.domain.model.ThemeMode
import com.vespelabs.fixit.core.domain.repository.ThemePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [ThemePreferences] over [android.content.SharedPreferences]. One value, written on the UI thread
 * with `apply()` (memory first, disk in the background), so no coroutine is needed and the toggle
 * flips on the same frame.
 *
 * DataStore would be the modern choice, but for a single enum it would add a dependency, a
 * serializer and a coroutine scope for no benefit. Swap the implementation, not the interface, if
 * the preference set grows.
 *
 * [Singleton] together with the `@Binds` in the DI module is what makes every screen observe the
 * same instance: the app shell and the profile screens must not hold different copies.
 */
@Singleton
class SharedPreferencesThemePreferences @Inject constructor(
    @ApplicationContext context: Context,
) : ThemePreferences {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(readStoredMode())
    override val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    override fun setMode(mode: ThemeMode) {
        _mode.value = mode
        prefs.edit().putString(KEY, mode.name).apply()
    }

    /** An unknown name (a value written by a newer build) reads as SYSTEM instead of crashing. */
    private fun readStoredMode(): ThemeMode {
        val stored = prefs.getString(KEY, null) ?: return ThemeMode.SYSTEM
        return ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.SYSTEM
    }

    private companion object {
        const val FILE = "fixit_prefs"
        const val KEY = "theme_mode"
    }
}
