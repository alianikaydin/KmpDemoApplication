package com.anksoft.myapplication.core.storage

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
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

    // N2
    @Test
    fun replaceTokensStoresBothTokensWhenTheRefreshTokenIsStillTheExpectedOne() {
        val sessionManager = SessionManager(MapSettings()).apply { saveAll() }

        val replaced = sessionManager.replaceTokensIfRefreshTokenIs("refresh-456", "access-2", "refresh-2")

        assertThat(replaced).isTrue()
        assertThat(sessionManager.getToken()).isEqualTo("access-2")
        assertThat(sessionManager.getRefreshToken()).isEqualTo("refresh-2")
    }

    // N2
    @Test
    fun replaceTokensWritesNothingWhenAnotherSessionOwnsTheStore() {
        val sessionManager = SessionManager(MapSettings()).apply { saveAll() }

        val replaced = sessionManager.replaceTokensIfRefreshTokenIs("refresh-of-account-a", "access-2", "refresh-2")

        assertThat(replaced).isFalse()
        assertThat(sessionManager.getToken()).isEqualTo("access-123")
        assertThat(sessionManager.getRefreshToken()).isEqualTo("refresh-456")
    }

    // N2
    @Test
    fun replaceTokensWritesNothingWhenThereIsNoSession() {
        val settings = MapSettings()
        val sessionManager = SessionManager(settings)

        val replaced = sessionManager.replaceTokensIfRefreshTokenIs("refresh-456", "access-2", "refresh-2")

        assertThat(replaced).isFalse()
        assertThat(settings.keys).isEmpty()
    }

    // N2
    @Test
    fun theSessionCanBeReadAndWrittenInsideTheSessionLock() {
        val sessionManager = SessionManager(MapSettings())

        val seen = sessionManager.withSessionLock {
            sessionManager.saveToken("access-123")
            sessionManager.getToken()
        }

        assertThat(seen).isEqualTo("access-123")
    }

    private fun SessionManager.saveAll() {
        saveToken("access-123")
        saveRefreshToken("refresh-456")
        saveUserId("u1")
        saveUserEmail("user@example.com")
        saveUserName("Test User")
    }
}
