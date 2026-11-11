package com.fusionlite.data

import android.content.Context

/**
 * How the app picks its colour scheme.
 *
 * [SYSTEM] is the default: the app follows the phone's dark-mode setting until
 * the user explicitly pins a theme with the switch. The choice is a device
 * preference, so it is kept in SharedPreferences and survives sign-out,
 * rotation and restarts (like [TariffStore]).
 */
enum class ThemeMode(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    val isExplicit: Boolean get() = this != SYSTEM

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

class ThemeStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("fusionlite.theme", Context.MODE_PRIVATE)

    fun mode(): ThemeMode = ThemeMode.fromStorage(prefs.getString(KEY_MODE, null))

    fun save(mode: ThemeMode) {
        prefs.edit().putString(KEY_MODE, mode.storageValue).apply()
    }

    private companion object {
        const val KEY_MODE = "theme_mode"
    }
}
