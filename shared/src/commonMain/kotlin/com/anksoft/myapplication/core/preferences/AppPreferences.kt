package com.anksoft.myapplication.core.preferences

import kotlinx.coroutines.flow.StateFlow

/**
 * Non-sensitive user preferences (the app language and theme).
 *
 * This store is NOT secure and is kept apart from the session store on purpose, so logging out
 * never resets a preference. Never write session or identity data (tokens, user id, e-mail) here.
 */
interface AppPreferences {

    /** The chosen language; its value is already correct when the app first draws. */
    val language: StateFlow<AppLanguage>

    fun setLanguage(language: AppLanguage)

    /** The chosen theme; its value is already correct when the app first draws. */
    val themeMode: StateFlow<ThemeMode>

    fun setThemeMode(themeMode: ThemeMode)
}
