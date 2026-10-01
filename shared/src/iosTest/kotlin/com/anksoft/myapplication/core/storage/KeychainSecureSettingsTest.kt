package com.anksoft.myapplication.core.storage

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.Test

class KeychainSecureSettingsTest {

    @AfterTest
    fun tearDown() {
        // Bypass SessionManager so a Keychain error fails the test with its OSStatus.
        val keychain = createSessionKeychain()
        SessionManager.SESSION_KEYS.forEach(keychain::remove)
    }

    @Test
    fun keychainRoundTripsAValue() {
        val keychain = createSessionKeychain()

        keychain.putString(SessionManager.KEY_TOKEN, "access-123")

        assertThat(keychain.getStringOrNull(SessionManager.KEY_TOKEN)).isEqualTo("access-123")
    }

    @Test
    fun sessionIsStoredInKeychainAndNotInUserDefaults() {
        val sessionManager = SessionManager(createSecureSettings())

        sessionManager.saveToken("access-123")
        sessionManager.saveRefreshToken("refresh-456")

        assertThat(createSessionKeychain().getStringOrNull(SessionManager.KEY_TOKEN)).isEqualTo("access-123")
        val userDefaults = NSUserDefaults.standardUserDefaults
        assertThat(userDefaults.stringForKey(SessionManager.KEY_TOKEN)).isNull()
        assertThat(userDefaults.stringForKey(SessionManager.KEY_REFRESH_TOKEN)).isNull()
    }

    @Test
    fun sessionSurvivesNewSettingsInstance() {
        SessionManager(createSecureSettings()).saveToken("access-123")

        val reopened = SessionManager(createSecureSettings())

        assertThat(reopened.getToken()).isEqualTo("access-123")
    }
}
