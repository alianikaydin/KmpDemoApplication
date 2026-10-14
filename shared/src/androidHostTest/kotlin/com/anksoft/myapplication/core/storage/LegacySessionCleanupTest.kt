package com.anksoft.myapplication.core.storage

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.anksoft.myapplication.core.logging.NoOpLogger
import com.russhwolf.settings.MapSettings
import kotlin.test.Test

class LegacySessionCleanupTest {

    private val legacy = MapSettings()
    private val secure = MapSettings()

    private fun create() = createEncryptedSessionStore(legacy, secure, FakeSessionCipher(), NoOpLogger)

    private fun seedLegacySession() {
        SessionManager.SESSION_KEYS.forEach { legacy.putString(it, "plain-value-for-$it") }
    }

    // AC-4
    @Test
    fun removesEverySessionKeyFromThePlainFile() {
        seedLegacySession()

        create()

        assertThat(legacy.keys).isEmpty()
    }

    // AC-4
    @Test
    fun keepsKeysThatAreNotPartOfTheSession() {
        seedLegacySession()
        legacy.putString("unrelated_pref", "keep")

        create()

        assertThat(legacy.keys).isEqualTo(setOf("unrelated_pref"))
    }

    // AC-4
    @Test
    fun doesNotMigrateAnythingIntoTheEncryptedStore() {
        seedLegacySession()

        val settings = create()

        assertThat(secure.keys).isEmpty()
        assertThat(settings.getStringOrNull(SessionManager.KEY_TOKEN)).isNull()
        assertThat(SessionManager(settings).getToken()).isNull()
    }

    // AC-4b
    @Test
    fun aFailingRemovalDoesNotStopTheStoreFromBeingCreated() {
        seedLegacySession()
        val failing = ThrowingSettings(
            delegate = legacy,
            failRemoveFor = setOf(SessionManager.KEY_TOKEN),
        )

        val settings = createEncryptedSessionStore(failing, secure, FakeSessionCipher(), NoOpLogger)

        assertThat(legacy.hasKey(SessionManager.KEY_TOKEN)).isEqualTo(true)
        assertThat(legacy.hasKey(SessionManager.KEY_REFRESH_TOKEN)).isEqualTo(false)
        assertThat(settings.getStringOrNull(SessionManager.KEY_TOKEN)).isNull()
    }

    @Test
    fun anEmptyPlainFileIsLeftUntouched() {
        create()

        assertThat(legacy.keys).isEmpty()
        assertThat(secure.keys).isEmpty()
    }
}
