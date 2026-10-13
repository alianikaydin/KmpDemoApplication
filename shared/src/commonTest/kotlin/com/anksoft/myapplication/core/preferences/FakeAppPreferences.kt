package com.anksoft.myapplication.core.preferences

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeAppPreferences(
    initial: AppLanguage = AppLanguage.SYSTEM,
    initialThemeMode: ThemeMode = ThemeMode.SYSTEM
) : AppPreferences {

    private val _language = MutableStateFlow(initial)
    override val language: StateFlow<AppLanguage> = _language

    /** Every language passed to [setLanguage], in order. */
    val setLanguageCalls = mutableListOf<AppLanguage>()

    override fun setLanguage(language: AppLanguage) {
        setLanguageCalls += language
        _language.value = language
    }

    private val _themeMode = MutableStateFlow(initialThemeMode)
    override val themeMode: StateFlow<ThemeMode> = _themeMode

    /** Every theme passed to [setThemeMode], in order. */
    val setThemeModeCalls = mutableListOf<ThemeMode>()

    override fun setThemeMode(themeMode: ThemeMode) {
        setThemeModeCalls += themeMode
        _themeMode.value = themeMode
    }
}
