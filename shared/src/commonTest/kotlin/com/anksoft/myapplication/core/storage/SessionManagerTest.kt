package com.anksoft.myapplication.core.storage

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
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

    private fun SessionManager.saveAll() {
        saveToken("access-123")
        saveRefreshToken("refresh-456")
        saveUserId("u1")
        saveUserEmail("user@example.com")
        saveUserName("Test User")
    }
}
