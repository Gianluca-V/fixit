package com.vespelabs.fixit.core.domain.model

/**
 * How the app resolves light and dark.
 *
 * [SYSTEM] follows the device and is the default: a fresh install should look like the rest of the
 * phone. It stops applying as soon as the user picks a theme explicitly, and the profile screen
 * offers a way back to it.
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }
