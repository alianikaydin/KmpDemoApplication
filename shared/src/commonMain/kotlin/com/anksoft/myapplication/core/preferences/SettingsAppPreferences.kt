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
 * [AppPreferences] on top of a [Settings] store that is not the session store.
 * Reads and writes never throw: a broken store must not stop the app from starting.
 */
class SettingsAppPreferences(
    private val settings: Settings,
    private val logger: AppLogger
) : AppPreferences {

    private val _language = MutableStateFlow(readLanguage())
    override val language: StateFlow<AppLanguage> = _language.asStateFlow()

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
}
