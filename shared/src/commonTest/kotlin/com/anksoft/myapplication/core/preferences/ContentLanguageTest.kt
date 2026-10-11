package com.anksoft.myapplication.core.preferences

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ContentLanguageTest {

    private val preferences = FakeAppPreferences()

    private fun contentLanguage(deviceLanguage: String) =
        ContentLanguage(preferences, deviceLanguage = { deviceLanguage })

    // AC-24
    @Test
    fun systemDefaultUsesTheDeviceLanguage() {
        assertThat(contentLanguage("tr").current()).isEqualTo("tr")
    }

    // AC-24
    @Test
    fun anExplicitInAppChoiceWinsOverTheDeviceLanguage() {
        preferences.setLanguage(AppLanguage.TURKISH)

        assertThat(contentLanguage("en").current()).isEqualTo("tr")
    }

    // AC-24
    @Test
    fun aDeviceLanguageTheAppDoesNotTranslateIsPassedOnAndTheBackendFallsBackToEnglish() {
        assertThat(contentLanguage("de").current()).isEqualTo("de")
    }

    // AC-24
    @Test
    fun theTagFlowFollowsTheInAppChoiceAndSkipsRepeats() = runTest {
        val contentLanguage = contentLanguage("en")

        contentLanguage.tag.test {
            assertThat(awaitItem()).isEqualTo("en")

            preferences.setLanguage(AppLanguage.TURKISH)
            assertThat(awaitItem()).isEqualTo("tr")

            preferences.setLanguage(AppLanguage.ENGLISH)
            assertThat(awaitItem()).isEqualTo("en")

            // System default on an English device is still "en": nothing new to report.
            preferences.setLanguage(AppLanguage.SYSTEM)
            expectNoEvents()
        }
    }
}
