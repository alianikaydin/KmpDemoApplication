package com.anksoft.myapplication.core.storage

import com.russhwolf.settings.Settings

/**
 * One-time preparation of secure session storage on the first launch of an install.
 *
 * - Removes session keys left in [secure] by a previous install (Keychain entries
 *   survive app deletion on iOS, while [installMarker] does not).
 * - Moves session keys from [legacy] into [secure], then deletes them from [legacy].
 * - Records completion in [installMarker] so it runs once per install.
 *
 * Throws if the underlying storage does; the caller decides how to handle it.
 * Nothing is marked as done on failure, so the next launch retries.
 */
internal class SessionStorageMigrator(
    private val legacy: Settings,
    private val secure: Settings,
    private val installMarker: Settings,
) {

    fun migrate() {
        if (installMarker.getBoolean(KEY_MIGRATED, defaultValue = false)) return

        SessionManager.SESSION_KEYS.forEach(secure::remove)

        val legacyValues = SessionManager.SESSION_KEYS.mapNotNull { key ->
            legacy.getStringOrNull(key)?.let { value -> key to value }
        }
        legacyValues.forEach { (key, value) -> secure.putString(key, value) }

        val copied = legacyValues.all { (key, value) -> secure.getStringOrNull(key) == value }
        if (!copied) return

        SessionManager.SESSION_KEYS.forEach(legacy::remove)
        installMarker.putBoolean(KEY_MIGRATED, true)
    }

    private companion object {
        const val KEY_MIGRATED = "secure_session_storage_ready"
    }
}
