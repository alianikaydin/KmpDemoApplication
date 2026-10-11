package com.anksoft.myapplication.core.analytics

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.anksoft.myapplication.core.analytics.FakeAnalytics.Call
import com.anksoft.myapplication.core.config.AppEnvironment
import com.anksoft.myapplication.core.consent.FakeConsentManager
import com.anksoft.myapplication.core.consent.OptionalDataConsent
import com.anksoft.myapplication.core.logging.LogSeverity
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.recordingLogger
import com.anksoft.myapplication.core.preferences.AppLanguage
import com.anksoft.myapplication.core.preferences.FakeAppPreferences
import kotlin.test.Test

/** The send gate and the parameters of a sent event. */
class AnalyticsTrackerTest {

    private val homeView = AnalyticsEvent.ScreenView(AnalyticsScreen.HOME)

    // AC-4, AC-12, AC-15
    @Test
    fun grantedInStageSendsScreenViewWithNameClassAndLanguage() {
        val analytics = FakeAnalytics()
        val tracker = testTracker(analytics = analytics, environment = AppEnvironment.STAGE)

        tracker.track(homeView)

        assertThat(analytics.events).containsExactly(
            Call.Event(
                "screen_view",
                mapOf("screen_name" to "home", "screen_class" to "home", "language" to "en")
            )
        )
    }

    // AC-11
    @Test
    fun grantedInProdSends() {
        val analytics = FakeAnalytics()

        testTracker(analytics = analytics, environment = AppEnvironment.PROD).track(homeView)

        assertThat(analytics.events).hasSize(1)
    }

    // AC-5
    @Test
    fun deniedSendsNothing() {
        val analytics = FakeAnalytics()
        val tracker = testTracker(analytics = analytics, consent = FakeConsentManager(OptionalDataConsent.DENIED))

        tracker.track(homeView)

        assertThat(analytics.events).isEmpty()
    }

    // AC-6
    @Test
    fun unknownSendsNothing() {
        val analytics = FakeAnalytics()
        val tracker = testTracker(analytics = analytics, consent = FakeConsentManager(OptionalDataConsent.UNKNOWN))

        tracker.track(homeView)

        assertThat(analytics.events).isEmpty()
    }

    // AC-6, AC-9
    @Test
    fun eventWithoutConsentIsNotSentWhenConsentArrivesLater() {
        val analytics = FakeAnalytics()
        val consent = FakeConsentManager(OptionalDataConsent.UNKNOWN)
        val tracker = testTracker(analytics = analytics, consent = consent)

        tracker.track(homeView)
        consent.optionalDataConsent.value = OptionalDataConsent.GRANTED

        assertThat(analytics.events).isEmpty()

        tracker.track(homeView)

        assertThat(analytics.events).hasSize(1)
    }

    // AC-10
    @Test
    fun devNeverSendsEvenWhenGrantedButWritesADebugLine() {
        val analytics = FakeAnalytics()
        val (logger, writer) = recordingLogger()
        val tracker = testTracker(analytics = analytics, environment = AppEnvironment.DEV, logger = logger)

        tracker.track(homeView)

        assertThat(analytics.events).isEmpty()
        val entry = writer.entries.single { it.tag == LogTags.ANALYTICS }
        assertThat(entry.severity).isEqualTo(LogSeverity.DEBUG)
        assertThat(entry.message).isEqualTo(
            "event=screen_view screen_name=home screen_class=home language=en forwarded=false"
        )
    }

    // AC-10, AC-15
    @Test
    fun theDebugLineIsWrittenAlsoWhenTheEventIsForwarded() {
        val (logger, writer) = recordingLogger()
        val tracker = testTracker(logger = logger, environment = AppEnvironment.STAGE)

        tracker.track(AnalyticsEvent.ScreenView(AnalyticsScreen.SETTINGS))

        assertThat(writer.entries.single().message).isEqualTo(
            "event=screen_view screen_name=settings screen_class=settings language=en forwarded=true"
        )
    }

    // AC-13
    @Test
    fun languageFollowsTheInAppChoiceWithoutRestart() {
        val analytics = FakeAnalytics()
        val preferences = FakeAppPreferences(AppLanguage.ENGLISH)
        val tracker = testTracker(analytics = analytics, preferences = preferences)

        tracker.track(homeView)
        preferences.setLanguage(AppLanguage.TURKISH)
        tracker.track(homeView)

        assertThat(analytics.events.map { it.params.getValue("language") }).containsExactly("en", "tr")
    }

    // A6
    @Test
    fun systemLanguageUsesASupportedDeviceLanguage() {
        val analytics = FakeAnalytics()
        val tracker = testTracker(
            analytics = analytics,
            preferences = FakeAppPreferences(AppLanguage.SYSTEM),
            deviceLanguage = { "tr" }
        )

        tracker.track(homeView)

        assertThat(analytics.events.single().params.getValue("language")).isEqualTo("tr")
    }

    // A6, AC-14
    @Test
    fun systemLanguageFallsBackToEnglishForAnUnsupportedDeviceLanguage() {
        val analytics = FakeAnalytics()
        val tracker = testTracker(
            analytics = analytics,
            preferences = FakeAppPreferences(AppLanguage.SYSTEM),
            deviceLanguage = { "de" }
        )

        tracker.track(homeView)

        assertThat(analytics.events.single().params.getValue("language")).isEqualTo("en")
    }

    // AC-21
    @Test
    fun aThrowingVendorOnLogEventDoesNotCrashAndIsLogged() {
        val analytics = FakeAnalytics(throwOn = setOf(Call.Event::class))
        val (logger, writer) = recordingLogger()
        val tracker = testTracker(analytics = analytics, logger = logger)

        tracker.track(homeView)

        assertThat(analytics.events).hasSize(1)
        val warning = writer.entries.single { it.severity == LogSeverity.WARN }
        assertThat(warning.tag).isEqualTo(LogTags.ANALYTICS)
        assertThat(warning.message).isEqualTo("Analytics step failed step=logEvent")
    }
}
