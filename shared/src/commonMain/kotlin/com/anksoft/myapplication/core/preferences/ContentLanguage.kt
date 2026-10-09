package com.anksoft.myapplication.core.preferences

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The language to request backend-served text in (consent texts): the in-app choice, or the
 * device language while the app is on "System default". The backend falls back to English for a
 * language it has no text for.
 */
class ContentLanguage(
    private val preferences: AppPreferences,
    private val deviceLanguage: () -> String = ::deviceLanguageTag
) {
    /** Emits the current tag first, then again whenever the in-app choice changes it. */
    val tag: Flow<String> = preferences.language.map { resolve(it) }.distinctUntilChanged()

    fun current(): String = resolve(preferences.language.value)

    private fun resolve(language: AppLanguage): String = language.tag ?: deviceLanguage()
}
