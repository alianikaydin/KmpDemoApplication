package com.anksoft.myapplication.core.presentation.theme

/**
 * Markers for the theme that is drawn right now, read by UI automation (Maestro).
 * They carry no text or content description, so screen readers skip them.
 */
internal object ThemeTestTags {
    const val APP_THEME_LIGHT = "app_theme_light"
    const val APP_THEME_DARK = "app_theme_dark"
}
