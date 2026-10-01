package com.anksoft.myapplication.core.storage

import com.russhwolf.settings.Settings

/**
 * Single owner of session state. Method names are unchanged from the previous
 * version so App.kt and SettingsScreenModel keep compiling.
 *
 * Backed by [createSecureSettings] rather than a plain Settings(). Only the iOS
 * actual (Keychain) is encrypted at rest today; see createSecureSettings for
 * per-platform status.
 *
 * Storage failures (e.g. a Keychain error) are treated as "no value" so a broken
 * store sends the user to Login instead of crashing the app.
 */
class SessionManager(private val settings: Settings) {

    fun saveToken(token: String) {
        write(KEY_TOKEN, token)
    }

    fun getToken(): String? = read(KEY_TOKEN)

    fun saveRefreshToken(token: String) {
        write(KEY_REFRESH_TOKEN, token)
    }

    fun getRefreshToken(): String? = read(KEY_REFRESH_TOKEN)

    fun saveUserId(userId: String) {
        write(KEY_USER_ID, userId)
    }

    fun getUserId(): String? = read(KEY_USER_ID)

    fun saveUserEmail(email: String) {
        write(KEY_USER_EMAIL, email)
    }

    fun getUserEmail(): String? = read(KEY_USER_EMAIL)

    fun saveUserName(name: String) {
        write(KEY_USER_NAME, name)
    }

    fun getUserName(): String? = read(KEY_USER_NAME)

    /** Wipes every session key. Called on logout and on refresh failure (AC-5.3). */
    fun clear() {
        SESSION_KEYS.forEach(::delete)
    }

    private fun read(key: String): String? =
        runCatching { settings.getStringOrNull(key) }.getOrNull()

    private fun write(key: String, value: String) {
        runCatching { settings.putString(key, value) }
    }

    private fun delete(key: String) {
        runCatching { settings.remove(key) }
    }

    companion object {
        internal const val KEY_TOKEN = "auth_token"
        internal const val KEY_REFRESH_TOKEN = "refresh_token"
        internal const val KEY_USER_ID = "user_id"
        internal const val KEY_USER_EMAIL = "user_email"
        internal const val KEY_USER_NAME = "user_name"

        /** Every key this class owns; used by [SessionStorageMigrator]. */
        internal val SESSION_KEYS = listOf(
            KEY_TOKEN,
            KEY_REFRESH_TOKEN,
            KEY_USER_ID,
            KEY_USER_EMAIL,
            KEY_USER_NAME,
        )
    }
}
