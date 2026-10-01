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
        SessionManager(createSecureSettings()).clear()
    }

    @Test
    fun sessionIsStoredInKeychainAndNotInUserDefaults() {
        val sessionManager = SessionManager(createSecureSettings())

        sessionManager.saveToken("access-123")
        sessionManager.saveRefreshToken("refresh-456")

        assertThat(sessionManager.getToken()).isEqualTo("access-123")
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
