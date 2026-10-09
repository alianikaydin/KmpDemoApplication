package com.anksoft.myapplication.core.storage

import assertk.assertThat
import assertk.assertions.contains
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

    // AC-27
    @Test
    fun theConsentCacheHoldsOnlyTheStatusAndTheTextVersion() {
        val settings = MapSettings()

        SessionManager(settings).saveConsent("granted", 2)

        assertThat(settings.keys).isEqualTo(setOf("consent_status", "consent_text_version"))
        assertThat(settings.getStringOrNull("consent_status")).isEqualTo("granted")
        assertThat(settings.getStringOrNull("consent_text_version")).isEqualTo("2")
    }

    // AC-27
    @Test
    fun savingAConsentWithoutAVersionRemovesTheOldVersion() {
        val sessionManager = SessionManager(MapSettings())
        sessionManager.saveConsent("granted", 2)

        sessionManager.saveConsent("none", null)

        assertThat(sessionManager.getConsentStatus()).isEqualTo("none")
        assertThat(sessionManager.getConsentTextVersion()).isNull()
    }

    // AC-18
    @Test
    fun clearRemovesTheConsentCacheWithTheRestOfTheSession() {
        val settings = MapSettings()
        val sessionManager = SessionManager(settings).apply { saveAll() }

        sessionManager.clear()

        assertThat(sessionManager.getConsentStatus()).isNull()
        assertThat(sessionManager.getConsentTextVersion()).isNull()
        assertThat(settings.keys).isEmpty()
    }

    // AC-18
    @Test
    fun theConsentKeysAreSessionKeysSoTheIosMigratorAndLogoutCoverThem() {
        assertThat(SessionManager.SESSION_KEYS).contains(SessionManager.KEY_CONSENT_STATUS)
        assertThat(SessionManager.SESSION_KEYS).contains(SessionManager.KEY_CONSENT_TEXT_VERSION)
    }

    // AC-18
    @Test
    fun clearConsentLeavesTheTokensAlone() {
        val sessionManager = SessionManager(MapSettings()).apply { saveAll() }

        sessionManager.clearConsent()

        assertThat(sessionManager.getConsentStatus()).isNull()
        assertThat(sessionManager.getToken()).isEqualTo("access-123")
    }

    // AC-18
    @Test
    fun aConsentAnswerIsCachedOnlyWhileTheSessionOfThatUserIsStored() {
        val sessionManager = SessionManager(MapSettings()).apply { saveAll() }

        assertThat(sessionManager.saveConsentForUser("u1", "denied", 1)).isTrue()
        assertThat(sessionManager.getConsentStatus()).isEqualTo("denied")

        assertThat(sessionManager.saveConsentForUser("u2", "granted", 1)).isFalse()
        assertThat(sessionManager.getConsentStatus()).isEqualTo("denied")
    }

    // AC-18
    @Test
    fun aConsentAnswerAfterLogoutIsNotCached() {
        val sessionManager = SessionManager(MapSettings()).apply { saveAll() }
        sessionManager.clear()

        val stored = sessionManager.saveConsentForUser("u1", "granted", 1)

        assertThat(stored).isFalse()
        assertThat(sessionManager.getConsentStatus()).isNull()
    }

    private fun SessionManager.saveAll() {
        saveToken("access-123")
        saveRefreshToken("refresh-456")
        saveUserId("u1")
        saveUserEmail("user@example.com")
        saveUserName("Test User")
        saveConsent("granted", 1)
    }
}
