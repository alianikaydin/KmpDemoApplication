package com.anksoft.myapplication.core.storage

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set

/**
 * Single owner of session state. Method names are unchanged from the previous
 * version so App.kt and SettingsScreenModel keep compiling.
 *
 * Backed by [createSecureSettings] rather than a plain Settings() so tokens are
 * not written in cleartext on Android. NOTE: only the Android actual is
 * hardware-backed today; see createSecureSettings for per-platform status.
 */
class SessionManager(private val settings: Settings) {

    fun saveToken(token: String) {
        settings[KEY_TOKEN] = token
    }

    fun getToken(): String? = settings[KEY_TOKEN]

    fun saveRefreshToken(token: String) {
        settings[KEY_REFRESH_TOKEN] = token
    }

    fun getRefreshToken(): String? = settings[KEY_REFRESH_TOKEN]

    fun saveUserId(userId: String) {
        settings[KEY_USER_ID] = userId
    }

    fun getUserId(): String? = settings[KEY_USER_ID]

    fun saveUserEmail(email: String) {
        settings[KEY_USER_EMAIL] = email
    }

    fun getUserEmail(): String? = settings[KEY_USER_EMAIL]

    fun saveUserName(name: String) {
        settings[KEY_USER_NAME] = name
    }

    fun getUserName(): String? = settings[KEY_USER_NAME]

    /** Wipes every session key. Called on logout and on refresh failure (AC-5.3). */
    fun clear() {
        settings.remove(KEY_TOKEN)
        settings.remove(KEY_REFRESH_TOKEN)
        settings.remove(KEY_USER_ID)
        settings.remove(KEY_USER_EMAIL)
        settings.remove(KEY_USER_NAME)
    }

    companion object {
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
    }
}
