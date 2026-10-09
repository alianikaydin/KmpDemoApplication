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
 *
 * Every access goes through one re-entrant lock, and [withSessionLock] lets a caller run a
 * compound change (check, then write; clear, then notify) as one step against the other threads
 * that touch the session.
 */
class SessionManager(private val settings: Settings) {

    private val lock = createSessionLock()

    /**
     * Runs [block] while holding the session lock. The lock is re-entrant, so [block] may call
     * the other methods of this class. Keep it short and never suspend inside it.
     */
    fun <T> withSessionLock(block: () -> T): T = lock.withLock(block)

    /**
     * Stores a rotated token pair, but only if the stored refresh token is still
     * [expectedRefreshToken]. Returns false (and writes nothing) when the session changed since
     * the caller read it, for example because the user logged out and signed in again.
     */
    fun replaceTokensIfRefreshTokenIs(
        expectedRefreshToken: String,
        accessToken: String,
        refreshToken: String
    ): Boolean = withSessionLock {
        if (getRefreshToken() == expectedRefreshToken) {
            saveToken(accessToken)
            saveRefreshToken(refreshToken)
            true
        } else {
            false
        }
    }

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
        withSessionLock { SESSION_KEYS.forEach(::delete) }
    }

    private fun read(key: String): String? =
        withSessionLock { runCatching { settings.getStringOrNull(key) }.getOrNull() }

    private fun write(key: String, value: String) {
        withSessionLock { runCatching { settings.putString(key, value) } }
    }

    private fun delete(key: String) {
        withSessionLock { runCatching { settings.remove(key) } }
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
