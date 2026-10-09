package com.anksoft.myapplication.core.preferences

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.anksoft.myapplication.core.logging.LogSeverity
import com.anksoft.myapplication.core.logging.NoOpLogger
import com.anksoft.myapplication.core.logging.recordingLogger
import com.anksoft.myapplication.core.storage.ThrowingSettings
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SettingsAppPreferencesTest {

    private val settings = MapSettings()

    private fun preferences(store: Settings = settings) =
        SettingsAppPreferences(store, NoOpLogger)

    // AC-1, AC-2
    @Test
    fun languageIsSystemWhenNothingIsStored() {
        assertThat(preferences().language.value).isEqualTo(AppLanguage.SYSTEM)
    }

    // AC-6
    @Test
    fun settingALanguageIsPublishedToCollectors() = runTest {
        val preferences = preferences()

        preferences.language.test {
            assertThat(awaitItem()).isEqualTo(AppLanguage.SYSTEM)

            preferences.setLanguage(AppLanguage.TURKISH)

            assertThat(awaitItem()).isEqualTo(AppLanguage.TURKISH)
        }
    }

    // AC-7
    @Test
    fun newInstanceReadsTheLanguageStoredByThePreviousOne() {
        preferences().setLanguage(AppLanguage.TURKISH)

        assertThat(preferences().language.value).isEqualTo(AppLanguage.TURKISH)
    }

    // AC-8
    @Test
    fun choosingSystemDefaultRemovesTheStoredKey() {
        val preferences = preferences()
        preferences.setLanguage(AppLanguage.ENGLISH)

        preferences.setLanguage(AppLanguage.SYSTEM)

        assertThat(settings.getStringOrNull(LANGUAGE_KEY)).isNull()
        assertThat(preferences().language.value).isEqualTo(AppLanguage.SYSTEM)
    }

    // AC-7
    @Test
    fun unknownStoredValueFallsBackToSystem() {
        settings.putString(LANGUAGE_KEY, "klingon")

        assertThat(preferences().language.value).isEqualTo(AppLanguage.SYSTEM)
    }

    // AC-9
    @Test
    fun languageIsStoredUnderTheAppPrefsPrefix() {
        preferences().setLanguage(AppLanguage.TURKISH)

        assertThat(settings.keys).isEqualTo(setOf("app_prefs.language"))
        assertThat(settings.getStringOrNull("app_prefs.language")).isEqualTo("tr")
    }

    @Test
    fun failedReadFallsBackToSystemAndLogsAWarning() {
        val (logger, writer) = recordingLogger()
        val broken = ThrowingSettings(failReads = true)

        val preferences = SettingsAppPreferences(broken, logger)

        assertThat(preferences.language.value).isEqualTo(AppLanguage.SYSTEM)
        assertThat(writer.entries.map { it.severity }).contains(LogSeverity.WARN)
    }

    @Test
    fun failedWriteStillAppliesTheChoiceForThisSession() {
        val (logger, writer) = recordingLogger()
        val preferences = SettingsAppPreferences(ThrowingSettings(failWrites = true), logger)

        preferences.setLanguage(AppLanguage.TURKISH)

        assertThat(preferences.language.value).isEqualTo(AppLanguage.TURKISH)
        assertThat(writer.entries.map { it.severity }).contains(LogSeverity.WARN)
    }

    @Test
    fun failedRemoveStillAppliesSystemDefaultForThisSession() {
        val broken = ThrowingSettings(failRemoveFor = setOf(LANGUAGE_KEY))
        val preferences = preferences(broken)
        preferences.setLanguage(AppLanguage.TURKISH)

        preferences.setLanguage(AppLanguage.SYSTEM)

        assertThat(preferences.language.value).isEqualTo(AppLanguage.SYSTEM)
    }

    @Test
    fun changingTheLanguageLogsOneDebugEntryWithTheLanguageName() {
        val (logger, writer) = recordingLogger()
        val preferences = SettingsAppPreferences(settings, logger)

        preferences.setLanguage(AppLanguage.TURKISH)

        val entry = writer.entries.single()
        assertThat(entry.severity).isEqualTo(LogSeverity.DEBUG)
        assertThat(entry.message).isEqualTo("Language set to TURKISH")
    }

    // AC-1
    @Test
    fun themeIsSystemWhenNothingIsStored() {
        assertThat(preferences().themeMode.value).isEqualTo(ThemeMode.SYSTEM)
    }

    // AC-5
    @Test
    fun settingAThemeIsPublishedToCollectors() = runTest {
        val preferences = preferences()

        preferences.themeMode.test {
            assertThat(awaitItem()).isEqualTo(ThemeMode.SYSTEM)

            preferences.setThemeMode(ThemeMode.DARK)

            assertThat(awaitItem()).isEqualTo(ThemeMode.DARK)
        }
    }

    // AC-6
    @Test
    fun newInstanceReadsTheThemeStoredByThePreviousOne() {
        preferences().setThemeMode(ThemeMode.DARK)

        assertThat(preferences().themeMode.value).isEqualTo(ThemeMode.DARK)
    }

    // AC-1
    @Test
    fun choosingSystemThemeRemovesTheStoredKey() {
        val preferences = preferences()
        preferences.setThemeMode(ThemeMode.LIGHT)

        preferences.setThemeMode(ThemeMode.SYSTEM)

        assertThat(settings.getStringOrNull(THEME_KEY)).isNull()
        assertThat(preferences().themeMode.value).isEqualTo(ThemeMode.SYSTEM)
    }

    // AC-1
    @Test
    fun unknownStoredThemeFallsBackToSystem() {
        settings.putString(THEME_KEY, "sepia")

        assertThat(preferences().themeMode.value).isEqualTo(ThemeMode.SYSTEM)
    }

    // AC-15
    @Test
    fun themeIsStoredUnderTheAppPrefsPrefix() {
        preferences().setThemeMode(ThemeMode.DARK)

        assertThat(settings.keys).isEqualTo(setOf("app_prefs.theme"))
        assertThat(settings.getStringOrNull("app_prefs.theme")).isEqualTo("dark")
    }

    @Test
    fun failedThemeReadFallsBackToSystemAndLogsAWarning() {
        val (logger, writer) = recordingLogger()

        val preferences = SettingsAppPreferences(ThrowingSettings(failReads = true), logger)

        assertThat(preferences.themeMode.value).isEqualTo(ThemeMode.SYSTEM)
        assertThat(writer.entries.map { it.severity }).contains(LogSeverity.WARN)
    }

    // AC-5
    @Test
    fun failedThemeWriteStillAppliesTheChoiceForThisSession() {
        val (logger, writer) = recordingLogger()
        val preferences = SettingsAppPreferences(ThrowingSettings(failWrites = true), logger)

        preferences.setThemeMode(ThemeMode.DARK)

        assertThat(preferences.themeMode.value).isEqualTo(ThemeMode.DARK)
        assertThat(writer.entries.map { it.severity }).contains(LogSeverity.WARN)
    }

    @Test
    fun changingTheThemeLogsOneDebugEntryWithTheThemeName() {
        val (logger, writer) = recordingLogger()
        val preferences = SettingsAppPreferences(settings, logger)

        preferences.setThemeMode(ThemeMode.DARK)

        val entry = writer.entries.single()
        assertThat(entry.severity).isEqualTo(LogSeverity.DEBUG)
        assertThat(entry.message).isEqualTo("Theme set to DARK")
    }

    // AC-7
    @Test
    fun themeAndLanguageAreIndependent() {
        val preferences = preferences()

        preferences.setThemeMode(ThemeMode.DARK)
        preferences.setLanguage(AppLanguage.TURKISH)
        preferences.setThemeMode(ThemeMode.SYSTEM)

        assertThat(preferences().language.value).isEqualTo(AppLanguage.TURKISH)
        assertThat(preferences().themeMode.value).isEqualTo(ThemeMode.SYSTEM)
    }
}
