package com.anksoft.myapplication.core.storage

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlin.test.Test

class SessionStorageMigratorTest {

    private val legacy = MapSettings()
    private val secure = MapSettings()

    private fun migrate(secureStore: Settings = secure) =
        SessionStorageMigrator(legacy = legacy, secure = secureStore, installMarker = legacy).migrate()

    @Test
    fun migrateMovesLegacySessionKeysToSecureStorageAndDeletesThemFromLegacy() {
        legacy.putString(SessionManager.KEY_TOKEN, "access-123")
        legacy.putString(SessionManager.KEY_REFRESH_TOKEN, "refresh-456")
        legacy.putString(SessionManager.KEY_USER_EMAIL, "user@example.com")

        migrate()

        assertThat(secure.getStringOrNull(SessionManager.KEY_TOKEN)).isEqualTo("access-123")
        assertThat(secure.getStringOrNull(SessionManager.KEY_REFRESH_TOKEN)).isEqualTo("refresh-456")
        assertThat(secure.getStringOrNull(SessionManager.KEY_USER_EMAIL)).isEqualTo("user@example.com")
        SessionManager.SESSION_KEYS.forEach { key ->
            assertThat(legacy.getStringOrNull(key)).isNull()
        }
    }

    @Test
    fun migrateOnFreshInstallClearsStaleSecureEntries() {
        secure.putString(SessionManager.KEY_TOKEN, "stale-token")
        secure.putString(SessionManager.KEY_USER_ID, "stale-user")

        migrate()

        assertThat(secure.getStringOrNull(SessionManager.KEY_TOKEN)).isNull()
        assertThat(secure.getStringOrNull(SessionManager.KEY_USER_ID)).isNull()
    }

    @Test
    fun migrateRunsOncePerInstall() {
        migrate()
        secure.putString(SessionManager.KEY_TOKEN, "new-session-token")

        migrate()

        assertThat(secure.getStringOrNull(SessionManager.KEY_TOKEN)).isEqualTo("new-session-token")
    }

    @Test
    fun migrateKeepsLegacyDataAndRetriesWhenSecureWriteIsLost() {
        legacy.putString(SessionManager.KEY_TOKEN, "access-123")

        migrate(secureStore = WriteDroppingSettings(secure))

        assertThat(legacy.getStringOrNull(SessionManager.KEY_TOKEN)).isEqualTo("access-123")

        migrate()

        assertThat(secure.getStringOrNull(SessionManager.KEY_TOKEN)).isEqualTo("access-123")
    }

    @Test
    fun migrateLeavesUnrelatedLegacyKeysUntouched() {
        legacy.putString("theme", "dark")
        legacy.putString(SessionManager.KEY_TOKEN, "access-123")

        migrate()

        assertThat(legacy.getStringOrNull("theme")).isEqualTo("dark")
        assertThat(secure.getStringOrNull("theme")).isNull()
    }
}
