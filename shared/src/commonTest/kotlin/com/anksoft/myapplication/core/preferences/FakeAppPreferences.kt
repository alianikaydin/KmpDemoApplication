package com.anksoft.myapplication.core.preferences

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeAppPreferences(initial: AppLanguage = AppLanguage.SYSTEM) : AppPreferences {

    private val _language = MutableStateFlow(initial)
    override val language: StateFlow<AppLanguage> = _language

    /** Every language passed to [setLanguage], in order. */
    val setLanguageCalls = mutableListOf<AppLanguage>()

    override fun setLanguage(language: AppLanguage) {
        setLanguageCalls += language
        _language.value = language
    }
}
