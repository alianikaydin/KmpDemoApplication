package com.anksoft.myapplication.features.update.data

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.anksoft.myapplication.core.logging.LogSeverity
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.NoOpLogger
import com.anksoft.myapplication.core.logging.recordingLogger
import com.anksoft.myapplication.core.storage.ThrowingSettings
import com.anksoft.myapplication.features.update.domain.model.AppVersion
import com.russhwolf.settings.MapSettings
import kotlin.test.Test

class SettingsDismissedUpdateStoreTest {

    private val settings = MapSettings()
    private val version = AppVersion(1, 4, 0)

    // AC-8
    @Test
    fun nothingIsDismissedWhenNothingIsStored() {
        assertThat(SettingsDismissedUpdateStore(settings, NoOpLogger).dismissedLatestVersion()).isNull()
    }

    // AC-8
    @Test
    fun aNewInstanceReadsWhatThePreviousOneWrote() {
        SettingsDismissedUpdateStore(settings, NoOpLogger).setDismissedLatestVersion(version)

        assertThat(SettingsDismissedUpdateStore(settings, NoOpLogger).dismissedLatestVersion()).isEqualTo(version)
    }

    // AC-8
    @Test
    fun theKeyIsPrefixedSoItCannotCollideWithSessionKeys() {
        SettingsDismissedUpdateStore(settings, NoOpLogger).setDismissedLatestVersion(version)

        assertThat(DISMISSED_LATEST_VERSION_KEY.startsWith("app_prefs.")).isTrue()
        assertThat(settings.getStringOrNull(DISMISSED_LATEST_VERSION_KEY)).isEqualTo("1.4.0")
    }

    // AC-9
    @Test
    fun aLaterWriteReplacesTheEarlierOne() {
        val store = SettingsDismissedUpdateStore(settings, NoOpLogger)
        store.setDismissedLatestVersion(version)

        store.setDismissedLatestVersion(AppVersion(1, 5, 0))

        assertThat(store.dismissedLatestVersion()).isEqualTo(AppVersion(1, 5, 0))
    }

    // AC-8
    @Test
    fun anInvalidStoredValueIsTreatedAsNothingDismissedAndWarned() {
        settings.putString(DISMISSED_LATEST_VERSION_KEY, "not-a-version")
        val (logger, writer) = recordingLogger()

        val dismissed = SettingsDismissedUpdateStore(settings, logger).dismissedLatestVersion()

        assertThat(dismissed).isNull()
        assertThat(writer.entries.map { it.severity to it.tag })
            .contains(LogSeverity.WARN to LogTags.UPDATE)
    }

    // AC-14
    @Test
    fun aBrokenStoreNeverThrowsOnReadAndWarns() {
        val (logger, writer) = recordingLogger()
        val store = SettingsDismissedUpdateStore(ThrowingSettings(failReads = true), logger)

        assertThat(store.dismissedLatestVersion()).isNull()
        assertThat(writer.entries.map { it.severity to it.tag })
            .contains(LogSeverity.WARN to LogTags.UPDATE)
    }

    // AC-14
    @Test
    fun aBrokenStoreNeverThrowsOnWriteAndWarns() {
        val (logger, writer) = recordingLogger()
        val store = SettingsDismissedUpdateStore(ThrowingSettings(failWrites = true), logger)

        store.setDismissedLatestVersion(version)

        assertThat(writer.entries.map { it.severity to it.tag })
            .contains(LogSeverity.WARN to LogTags.UPDATE)
    }
}
