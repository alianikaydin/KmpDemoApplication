package com.anksoft.myapplication.features.update.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test

class StoreUrlsTest {

    // AC-3, AC-20
    @Test
    fun playStoreUrlNamesThePackage() {
        assertThat(StoreUrls.playStore("com.anksoft.myapplication"))
            .isEqualTo("https://play.google.com/store/apps/details?id=com.anksoft.myapplication")
    }

    // AC-20
    @Test
    fun playStoreUrlFollowsThePackageOfTheEnvironment() {
        assertThat(StoreUrls.playStore("com.anksoft.myapplication.dev"))
            .isEqualTo("https://play.google.com/store/apps/details?id=com.anksoft.myapplication.dev")
    }

    // AC-3
    @Test
    fun appStoreUrlNamesTheNumericId() {
        assertThat(StoreUrls.appStore("123456789")).isEqualTo("https://apps.apple.com/app/id123456789")
    }

    // AC-20
    @Test
    fun appStoreUrlIgnoresSurroundingWhitespace() {
        assertThat(StoreUrls.appStore(" 123456789 ")).isEqualTo("https://apps.apple.com/app/id123456789")
    }

    // AC-4, AC-20
    @Test
    fun appStoreUrlIsNullWhenTheIdIsMissingOrNotNumeric() {
        listOf(null, "", "  ", "PLACEHOLDER", "id123", "12 34", "123abc").forEach { id ->
            assertThat(StoreUrls.appStore(id), name = "appStore($id)").isNull()
        }
    }
}
