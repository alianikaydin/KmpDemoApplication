package com.anksoft.myapplication.core.preferences

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test

class AppLanguageTest {

    // AC-1
    @Test
    fun turkishTagResolvesToTurkish() {
        assertThat(AppLanguage.fromTag("tr")).isEqualTo(AppLanguage.TURKISH)
    }

    @Test
    fun englishTagResolvesToEnglish() {
        assertThat(AppLanguage.fromTag("en")).isEqualTo(AppLanguage.ENGLISH)
    }

    // AC-2
    @Test
    fun unsupportedBlankOrMissingTagFallsBackToSystem() {
        assertThat(AppLanguage.fromTag("de")).isEqualTo(AppLanguage.SYSTEM)
        assertThat(AppLanguage.fromTag("")).isEqualTo(AppLanguage.SYSTEM)
        assertThat(AppLanguage.fromTag(null)).isEqualTo(AppLanguage.SYSTEM)
    }

    @Test
    fun tagLookupIsCaseSensitiveSoStoredValuesStayExact() {
        assertThat(AppLanguage.fromTag("TR")).isEqualTo(AppLanguage.SYSTEM)
    }

    // AC-5
    @Test
    fun languageNamesAreWrittenInTheirOwnLanguage() {
        assertThat(AppLanguage.TURKISH.nativeName).isEqualTo("Türkçe")
        assertThat(AppLanguage.ENGLISH.nativeName).isEqualTo("English")
    }

    // AC-5
    @Test
    fun systemDefaultHasNoFixedNameBecauseItsLabelIsTranslated() {
        assertThat(AppLanguage.SYSTEM.nativeName).isNull()
        assertThat(AppLanguage.SYSTEM.tag).isNull()
    }

    @Test
    fun everyLanguageRoundTripsThroughItsTag() {
        AppLanguage.entries.forEach { language ->
            assertThat(AppLanguage.fromTag(language.tag)).isEqualTo(language)
        }
    }
}
