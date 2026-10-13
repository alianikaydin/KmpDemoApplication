package com.anksoft.myapplication.core.preferences

import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.debug
import com.anksoft.myapplication.core.logging.warn
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Key of the language tag. The `app_prefs.` prefix keeps it apart from session keys even when a platform shares one storage (web localStorage). */
internal const val LANGUAGE_KEY = "app_prefs.language"

/**
 * Key of the theme value (see [ThemeMode.storedValue]). The web page's inline script in
 * `webApp/src/webMain/resources/index.html` reads this key; keep both in sync.
 */
internal const val THEME_KEY = "app_prefs.theme"

/**
 * [AppPreferences] on top of a [Settings] store that is not the session store.
 * Reads and writes never throw: a broken store must not stop the app from starting.
 */
class SettingsAppPreferences(
    private val settings: Settings,
    private val logger: AppLogger
) : AppPreferences {

    private val _language = MutableStateFlow(readLanguage())
    override val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _themeMode = MutableStateFlow(readThemeMode())
    override val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    override fun setLanguage(language: AppLanguage) {
        val tag = language.tag
        runCatching {
            if (tag == null) settings.remove(LANGUAGE_KEY) else settings.putString(LANGUAGE_KEY, tag)
        }.onFailure { error ->
            logger.warn(LogTags.APP, error) { "Could not persist the language preference" }
        }
        // Update even when persisting failed, so the choice at least applies for this session.
        _language.value = language
        logger.debug(LogTags.APP) { "Language set to ${language.name}" }
    }

    private fun readLanguage(): AppLanguage {
        val stored = runCatching { settings.getStringOrNull(LANGUAGE_KEY) }
            .onFailure { error ->
                logger.warn(LogTags.APP, error) { "Could not read the language preference" }
            }
            .getOrNull()
        return AppLanguage.fromTag(stored)
    }

    override fun setThemeMode(themeMode: ThemeMode) {
        val stored = themeMode.storedValue
        runCatching {
            if (stored == null) settings.remove(THEME_KEY) else settings.putString(THEME_KEY, stored)
        }.onFailure { error ->
            logger.warn(LogTags.APP, error) { "Could not persist the theme preference" }
        }
        // Update even when persisting failed, so the choice at least applies for this session.
        _themeMode.value = themeMode
        logger.debug(LogTags.APP) { "Theme set to ${themeMode.name}" }
    }

    private fun readThemeMode(): ThemeMode {
        val stored = runCatching { settings.getStringOrNull(THEME_KEY) }
            .onFailure { error ->
                logger.warn(LogTags.APP, error) { "Could not read the theme preference" }
            }
            .getOrNull()
        return ThemeMode.fromStored(stored)
    }
}
