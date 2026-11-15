package dev.gladix.desktop.core

/**
 * The persisted theme choice and how it turns into a light/dark decision.
 *
 * Kept apart from the composables so the fallback rules (unknown value, first
 * start) can be asserted without a window.
 */
object ThemePreference {

    /** The values the settings screen offers, in display order. */
    val choices = listOf(
        PrefsSettings.THEME_SYSTEM,
        PrefsSettings.THEME_DARK,
        PrefsSettings.THEME_LIGHT
    )

    /** Anything unknown (a stale file, a hand edit) falls back to following the OS. */
    fun normalize(stored: String?): String =
        if (stored != null && stored in choices) stored else PrefsSettings.THEME_SYSTEM

    fun isDark(stored: String?, systemDark: Boolean): Boolean = when (normalize(stored)) {
        PrefsSettings.THEME_DARK -> true
        PrefsSettings.THEME_LIGHT -> false
        else -> systemDark
    }
}
