package com.anksoft.myapplication.core.preferences

/**
 * The theme the user chose: follow the device, or force light or dark.
 *
 * [storedValue] is what is persisted. The inline script in `webApp/src/webMain/resources/index.html`
 * reads the same values from localStorage (key [THEME_KEY]) so the page loads in the right
 * colors; if they change here, change them there too.
 */
enum class ThemeMode(val storedValue: String?) {
    SYSTEM(storedValue = null),
    LIGHT(storedValue = "light"),
    DARK(storedValue = "dark");

    /** The theme to draw: SYSTEM follows [systemInDarkTheme], LIGHT/DARK ignore it. */
    fun isDark(systemInDarkTheme: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDarkTheme
        LIGHT -> false
        DARK -> true
    }

    companion object {
        /** Unknown, blank or null values fall back to [SYSTEM]. */
        fun fromStored(value: String?): ThemeMode =
            entries.firstOrNull { it.storedValue != null && it.storedValue == value } ?: SYSTEM
    }
}
