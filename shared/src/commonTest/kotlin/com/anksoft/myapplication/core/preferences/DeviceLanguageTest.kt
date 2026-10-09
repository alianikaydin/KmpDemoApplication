package com.anksoft.myapplication.core.preferences

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class DeviceLanguageTest {

    // AC-24
    @Test
    fun aRegionalTagIsReducedToItsLanguage() {
        assertThat(primaryLanguageSubtag("tr-TR")).isEqualTo("tr")
        assertThat(primaryLanguageSubtag("en_US")).isEqualTo("en")
        assertThat(primaryLanguageSubtag("zh-Hant-TW")).isEqualTo("zh")
    }

    // AC-24
    @Test
    fun caseAndSurroundingSpacesAreNormalised() {
        assertThat(primaryLanguageSubtag(" TR ")).isEqualTo("tr")
    }

    // AC-24
    @Test
    fun missingOrEmptyInputFallsBackToEnglish() {
        assertThat(primaryLanguageSubtag(null)).isEqualTo("en")
        assertThat(primaryLanguageSubtag("")).isEqualTo("en")
        assertThat(primaryLanguageSubtag("  ")).isEqualTo("en")
        assertThat(primaryLanguageSubtag("-TR")).isEqualTo("en")
    }
}
