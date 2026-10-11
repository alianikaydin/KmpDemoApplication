package com.anksoft.myapplication.core.analytics

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isTrue
import com.anksoft.myapplication.core.preferences.AppLanguage
import kotlin.test.Test

/** Guards the Firebase limits for every event the app can send (docs/analytics-events.md). */
class AnalyticsCatalogTest {

    // Add every new AnalyticsEvent subtype here; the sealed `when`s in AnalyticsEvent.kt force the
    // mapping, and this list makes the rules below apply to it.
    private val catalog: List<AnalyticsEvent> = AnalyticsScreen.entries.map { AnalyticsEvent.ScreenView(it) }

    private val nameRule = Regex("^[a-zA-Z][a-zA-Z0-9_]{0,39}$")
    private val reservedPrefixes = listOf("firebase_", "google_", "ga_")

    private fun allLanguageValues(): List<String> {
        val deviceTags = listOf("tr", "en", "de", "")
        return AppLanguage.entries.flatMap { choice ->
            deviceTags.map { device -> analyticsLanguage(choice) { device } }
        }
    }

    // AC-24
    @Test
    fun everyEventAndParameterNameFollowsFirebaseRules() {
        assertThat(catalog).isNotEmpty()
        val names = catalog.flatMap { event ->
            listOf(event.vendorName) + event.vendorParams().keys + AnalyticsNames.LANGUAGE
        }

        names.forEach { name ->
            assertThat(nameRule.matches(name), name).isTrue()
            assertThat(reservedPrefixes.none { name.startsWith(it) }, name).isTrue()
        }
    }

    // AC-14, AC-24
    @Test
    fun everyParameterValueComesFromAFixedSetAndFitsTheLimit() {
        val fixed = AnalyticsScreen.entries.map { it.value }.toSet() +
            AppLanguage.entries.mapNotNull { it.tag } + "en"

        val values = catalog.flatMap { it.vendorParams().values } + allLanguageValues()

        values.forEach { value ->
            assertThat(value in fixed, value).isTrue()
            assertThat(value.length).isLessThanOrEqualTo(100)
        }
    }

    // AC-24
    @Test
    fun anEventStaysWithinTheParameterLimit() {
        catalog.forEach { event ->
            // The tracker adds `language` to the event's own parameters.
            assertThat(event.vendorParams().size + 1).isLessThanOrEqualTo(25)
        }
    }

    // AC-12
    @Test
    fun screenViewUsesTheVendorStandardNamesAndTheFixedScreenName() {
        val event = AnalyticsEvent.ScreenView(AnalyticsScreen.HOME)

        assertThat(event.vendorName).isEqualTo("screen_view")
        assertThat(event.vendorParams()).isEqualTo(mapOf("screen_name" to "home", "screen_class" to "home"))
        assertThat(AnalyticsNames.LANGUAGE).isEqualTo("language")
    }
}
