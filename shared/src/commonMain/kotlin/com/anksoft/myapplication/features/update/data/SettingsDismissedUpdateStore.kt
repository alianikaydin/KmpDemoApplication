package com.anksoft.myapplication.features.update.data

import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.warn
import com.anksoft.myapplication.features.update.domain.DismissedUpdateStore
import com.anksoft.myapplication.features.update.domain.model.AppVersion
import com.russhwolf.settings.Settings

/** Key of the dismissed latest version. The `app_prefs.` prefix keeps it apart from session keys on the web. */
internal const val DISMISSED_LATEST_VERSION_KEY = "app_prefs.update.dismissed_latest_version"

/**
 * [DismissedUpdateStore] on the app preferences [Settings] (not the session store), so a sign-out
 * does not bring the prompt back. Reads and writes never throw.
 */
class SettingsDismissedUpdateStore(
    private val settings: Settings,
    private val logger: AppLogger,
) : DismissedUpdateStore {

    override fun dismissedLatestVersion(): AppVersion? {
        val stored = runCatching { settings.getStringOrNull(DISMISSED_LATEST_VERSION_KEY) }
            .onFailure { error ->
                logger.warn(LogTags.UPDATE, error) { "Could not read the dismissed update version" }
            }
            .getOrNull() ?: return null
        return AppVersion.parse(stored).also { parsed ->
            if (parsed == null) logger.warn(LogTags.UPDATE) { "Ignoring an invalid dismissed update version" }
        }
    }

    override fun setDismissedLatestVersion(version: AppVersion) {
        runCatching { settings.putString(DISMISSED_LATEST_VERSION_KEY, version.toString()) }
            .onFailure { error ->
                logger.warn(LogTags.UPDATE, error) { "Could not persist the dismissed update version" }
            }
    }
}
