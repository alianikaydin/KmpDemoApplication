package com.anksoft.myapplication.core.analytics

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isEqualTo
import com.anksoft.myapplication.core.preferences.AppLanguage
import kotlin.test.Test

class AnalyticsLanguageTest {

    // AC-13
    @Test
    fun aChosenLanguageIsUsedWhateverTheDeviceSpeaks() {
        assertThat(analyticsLanguage(AppLanguage.TURKISH) { "en" }).isEqualTo("tr")
        assertThat(analyticsLanguage(AppLanguage.ENGLISH) { "tr" }).isEqualTo("en")
    }

    // AC-13
    @Test
    fun systemDefaultUsesASupportedDeviceLanguage() {
        assertThat(analyticsLanguage(AppLanguage.SYSTEM) { "tr" }).isEqualTo("tr")
    }

    // AC-14
    @Test
    fun systemDefaultFallsBackToEnglishForAnUnsupportedDeviceLanguage() {
        assertThat(analyticsLanguage(AppLanguage.SYSTEM) { "de" }).isEqualTo("en")
    }

    // AC-13
    @Test
    fun theDeviceLanguageIsNotAskedWhenTheUserChose() {
        var asked = false

        analyticsLanguage(AppLanguage.TURKISH) { asked = true; "en" }

        assertThat(asked).isFalse()
    }
}
