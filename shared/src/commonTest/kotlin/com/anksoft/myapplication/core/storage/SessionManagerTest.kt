package com.anksoft.myapplication.core.storage

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.anksoft.myapplication.core.logging.NoOpLogger
import com.anksoft.myapplication.core.preferences.AppLanguage
import com.anksoft.myapplication.core.preferences.LANGUAGE_KEY
import com.anksoft.myapplication.core.preferences.SettingsAppPreferences
import com.russhwolf.settings.MapSettings
import kotlin.test.Test

class SessionManagerTest {

    @Test
    fun getTokenReturnsNullWhenStorageReadThrows() {
        val settings = ThrowingSettings()
        val sessionManager = SessionManager(settings)
        sessionManager.saveToken("access-123")

        settings.failReads = true

        assertThat(sessionManager.getToken()).isNull()
    }

    @Test
    fun saveTokenDoesNotThrowWhenStorageWriteThrows() {
        val sessionManager = SessionManager(ThrowingSettings(failWrites = true))

        sessionManager.saveToken("access-123")

        assertThat(sessionManager.getToken()).isNull()
    }

    @Test
    fun clearRemovesAllSessionKeys() {
        val settings = MapSettings()
        val sessionManager = SessionManager(settings).apply { saveAll() }

        sessionManager.clear()

        assertThat(settings.keys).isEmpty()
    }

    @Test
    fun clearContinuesWhenOneKeyFailsToDelete() {
        val settings = ThrowingSettings(failRemoveFor = setOf(SessionManager.KEY_TOKEN))
        val sessionManager = SessionManager(settings).apply { saveAll() }

        sessionManager.clear()

        assertThat(settings.keys).isEqualTo(setOf(SessionManager.KEY_TOKEN))
    }

    // AC-9
    @Test
    fun clearKeepsTheLanguagePreferenceEvenWhenBothShareOneStorage() {
        // Worst case: on web the session and the preferences both live in localStorage.
        val shared = MapSettings()
        val sessionManager = SessionManager(shared).apply { saveAll() }
        SettingsAppPreferences(shared, NoOpLogger).setLanguage(AppLanguage.TURKISH)

        sessionManager.clear()

        assertThat(sessionManager.getToken()).isNull()
        assertThat(shared.keys).isEqualTo(setOf(LANGUAGE_KEY))
        assertThat(SettingsAppPreferences(shared, NoOpLogger).language.value)
            .isEqualTo(AppLanguage.TURKISH)
    }

    private fun SessionManager.saveAll() {
        saveToken("access-123")
        saveRefreshToken("refresh-456")
        saveUserId("u1")
        saveUserEmail("user@example.com")
        saveUserName("Test User")
    }
}
