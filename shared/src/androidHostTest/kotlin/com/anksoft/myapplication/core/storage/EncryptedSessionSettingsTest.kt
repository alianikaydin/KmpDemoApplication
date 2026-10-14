package com.anksoft.myapplication.core.storage

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.anksoft.myapplication.core.logging.LogSeverity
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.recordingLogger
import com.russhwolf.settings.MapSettings
import kotlin.test.Test

class EncryptedSessionSettingsTest {

    private val store = MapSettings()
    private val cipher = FakeSessionCipher()
    private val recorded = recordingLogger()
    private val settings = EncryptedSessionSettings(store, cipher, recorded.first)

    // AC-1
    @Test
    fun storedValuesAreNotPlainText() {
        settings.putString("auth_token", "access-123")

        assertThat(store.getStringOrNull("auth_token")).isNotNull().doesNotContain("access-123")
        assertThat(settings.getStringOrNull("auth_token")).isEqualTo("access-123")
    }

    // AC-2
    @Test
    fun aNewInstanceOverTheSameStoreReadsTheValue() {
        settings.putString("auth_token", "access-123")

        val reopened = EncryptedSessionSettings(store, cipher, recorded.first)

        assertThat(reopened.getStringOrNull("auth_token")).isEqualTo("access-123")
    }

    @Test
    fun getStringFallsBackToTheDefault() {
        assertThat(settings.getString("missing", "fallback")).isEqualTo("fallback")
    }

    // AC-3
    @Test
    fun removeDeletesTheValueFromStoreAndMemory() {
        settings.putString("auth_token", "access-123")

        settings.remove("auth_token")

        assertThat(settings.getStringOrNull("auth_token")).isNull()
        assertThat(store.keys).isEmpty()
    }

    // AC-3
    @Test
    fun clearDeletesEveryValue() {
        settings.putString("auth_token", "access-123")
        settings.putString("refresh_token", "refresh-123")

        settings.clear()

        assertThat(settings.getStringOrNull("auth_token")).isNull()
        assertThat(settings.getStringOrNull("refresh_token")).isNull()
        assertThat(store.keys).isEmpty()
    }

    // AC-6
    @Test
    fun undecryptableValueWipesTheWholeSessionAndTheKey() {
        settings.putString("auth_token", "access-123")
        settings.putString("refresh_token", "refresh-123")
        val reopened = EncryptedSessionSettings(store, cipher, recorded.first)
        cipher.failDecrypt = true

        assertThat(reopened.getStringOrNull("auth_token")).isNull()

        assertThat(store.keys).isEmpty()
        assertThat(cipher.deleteKeyCalls).isEqualTo(1)
        cipher.failDecrypt = false
        assertThat(reopened.getStringOrNull("refresh_token")).isNull()
    }

    // AC-6
    @Test
    fun valueFromAnotherKeyNameIsRejectedAndWipes() {
        settings.putString("auth_token", "access-123")
        store.putString("refresh_token", store.getString("auth_token", ""))
        val reopened = EncryptedSessionSettings(store, cipher, recorded.first)

        assertThat(reopened.getStringOrNull("refresh_token")).isNull()
        assertThat(store.keys).isEmpty()
    }

    // AC-6
    @Test
    fun theWipeIsLoggedWithoutAnyValue() {
        settings.putString("auth_token", "access-123")
        val reopened = EncryptedSessionSettings(store, cipher, recorded.first)
        cipher.failDecrypt = true

        reopened.getStringOrNull("auth_token")

        val entry = recorded.second.entries.single()
        assertThat(entry.severity).isEqualTo(LogSeverity.WARN)
        assertThat(entry.tag).isEqualTo(LogTags.SESSION)
        assertThat(entry.message).doesNotContain("access-123")
    }

    // AC-7
    @Test
    fun encryptionFailureStoresNothingAndDropsTheOldValue() {
        settings.putString("auth_token", "old-token")
        cipher.failEncrypt = true

        assertFailure { settings.putString("auth_token", "new-secret-token") }
            .isInstanceOf(SessionCipherException::class)

        assertThat(store.keys).isEmpty()
        assertThat(settings.getStringOrNull("auth_token")).isNull()
    }

    // AC-7
    @Test
    fun encryptionFailureNeverWritesThePlainValue() {
        cipher.failEncrypt = true

        runCatching { settings.putString("auth_token", "new-secret-token") }

        assertThat(store.keys).isEmpty()
        recorded.second.entries.forEach { assertThat(it.message).doesNotContain("new-secret-token") }
    }

    // AC-7
    @Test
    fun encryptionFailureKeepsOtherValues() {
        settings.putString("refresh_token", "refresh-123")
        cipher.failEncrypt = true

        runCatching { settings.putString("auth_token", "x") }

        assertThat(settings.getStringOrNull("refresh_token")).isEqualTo("refresh-123")
        assertThat(store.keys).isEqualTo(setOf("refresh_token"))
    }

    // AC-7
    @Test
    fun nonStringTypesAreRejected() {
        assertFailure { settings.putInt("n", 1) }.isInstanceOf(UnsupportedOperationException::class)
        assertFailure { settings.putLong("n", 1L) }.isInstanceOf(UnsupportedOperationException::class)
        assertFailure { settings.putBoolean("n", true) }.isInstanceOf(UnsupportedOperationException::class)
        assertFailure { settings.putFloat("n", 1f) }.isInstanceOf(UnsupportedOperationException::class)
        assertFailure { settings.putDouble("n", 1.0) }.isInstanceOf(UnsupportedOperationException::class)
        assertFailure { settings.getInt("n", 0) }.isInstanceOf(UnsupportedOperationException::class)
        assertFailure { settings.getBooleanOrNull("n") }.isInstanceOf(UnsupportedOperationException::class)
        assertThat(store.keys).isEmpty()
    }

    // AC-6, AC-7
    @Test
    fun sessionManagerRoundTripsThroughTheEncryptedStore() {
        val manager = SessionManager(settings)

        manager.saveToken("access-123")
        manager.saveRefreshToken("refresh-123")
        manager.saveUserId("user-1")

        assertThat(manager.getToken()).isEqualTo("access-123")
        assertThat(manager.getRefreshToken()).isEqualTo("refresh-123")
        assertThat(manager.getUserId()).isEqualTo("user-1")
        store.keys.forEach { assertThat(store.getString(it, "")).doesNotContain("123") }
    }

    // AC-7
    @Test
    fun sessionManagerReadsNoTokenAfterAnEncryptionFailure() {
        val manager = SessionManager(settings)
        cipher.failEncrypt = true

        manager.saveToken("access-123")

        assertThat(manager.getToken()).isNull()
    }

    // AC-6
    @Test
    fun sessionManagerReadsNoTokenAfterADecryptionFailure() {
        SessionManager(settings).saveToken("access-123")
        val manager = SessionManager(EncryptedSessionSettings(store, cipher, recorded.first))
        cipher.failDecrypt = true

        assertThat(manager.getToken()).isNull()
        assertThat(store.keys).isEmpty()
    }

    @Test
    fun keysReflectTheEncryptedStore() {
        settings.putString("auth_token", "a")

        assertThat(settings.keys).contains("auth_token")
        assertThat(settings.size).isEqualTo(1)
        assertThat(settings.hasKey("auth_token")).isEqualTo(true)
    }
}
