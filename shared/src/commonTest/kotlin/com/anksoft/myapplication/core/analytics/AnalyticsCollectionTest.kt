package com.anksoft.myapplication.core.analytics

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isNotEmpty
import com.anksoft.myapplication.core.analytics.FakeAnalytics.Call
import com.anksoft.myapplication.core.config.AppEnvironment
import com.anksoft.myapplication.core.consent.FakeConsentManager
import com.anksoft.myapplication.core.consent.OptionalDataConsent.DENIED
import com.anksoft.myapplication.core.consent.OptionalDataConsent.GRANTED
import com.anksoft.myapplication.core.consent.OptionalDataConsent.UNKNOWN
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * `start()`: the vendor's collection flag and data reset follow the consent.
 *
 * The three tests of what `start()` does by itself use a plain `runTest` (StandardTestDispatcher):
 * a launch there is only queued, so they assert right after `start()` without `runCurrent()` and
 * would fail if the collector were not started UNDISPATCHED. Under an unconfined test dispatcher
 * even a default launch runs at once, which would prove nothing. The change tests below use the
 * unconfined one so a new consent value reaches the collector immediately.
 */
class AnalyticsCollectionTest {

    private val homeView = AnalyticsEvent.ScreenView(AnalyticsScreen.HOME)
    private val off = Call.SetCollection(false)
    private val on = Call.SetCollection(true)

    // AC-7
    @Test
    fun startWithUnknownTurnsCollectionOffAndResetsBeforeReturning() = runTest {
        val analytics = FakeAnalytics()
        val tracker = testTracker(analytics = analytics, consent = FakeConsentManager(UNKNOWN), scope = backgroundScope)

        tracker.start()

        assertThat(analytics.calls).containsExactly(off, Call.Reset)
    }

    // AC-4
    @Test
    fun startWithGrantedInStageTurnsCollectionOnWithoutReset() = runTest {
        val analytics = FakeAnalytics()
        val tracker = testTracker(analytics = analytics, consent = FakeConsentManager(GRANTED), scope = backgroundScope)

        tracker.start()

        assertThat(analytics.calls).containsExactly(on)
    }

    // AC-8
    @Test
    fun grantedToDeniedTurnsCollectionOffThenResets() = runTest(UnconfinedTestDispatcher()) {
        val analytics = FakeAnalytics()
        val consent = FakeConsentManager(GRANTED)
        testTracker(analytics = analytics, consent = consent, scope = backgroundScope).start()

        consent.optionalDataConsent.value = DENIED

        assertThat(analytics.calls).containsExactly(on, off, Call.Reset)
    }

    // AC-8, AC-17
    @Test
    fun grantedToUnknownOnSignOutTurnsOffAndResets() = runTest(UnconfinedTestDispatcher()) {
        val analytics = FakeAnalytics()
        val consent = FakeConsentManager(GRANTED)
        testTracker(analytics = analytics, consent = consent, scope = backgroundScope).start()

        consent.optionalDataConsent.value = UNKNOWN

        assertThat(analytics.calls).containsExactly(on, off, Call.Reset)
    }

    // AC-17
    @Test
    fun signOutThenAnotherAccountWithoutADecisionSendsNothing() = runTest(UnconfinedTestDispatcher()) {
        val analytics = FakeAnalytics()
        val consent = FakeConsentManager(GRANTED)
        val tracker = testTracker(analytics = analytics, consent = consent, scope = backgroundScope)
        tracker.start()

        consent.optionalDataConsent.value = UNKNOWN
        // The next account signs in: its decision is unknown until it is fetched.
        tracker.track(homeView)

        assertThat(analytics.events).isEmpty()
        assertThat(analytics.calls.filterIsInstance<Call.Reset>()).isNotEmpty()
    }

    // AC-9
    @Test
    fun unknownToGrantedTurnsCollectionOnWithoutRestart() = runTest(UnconfinedTestDispatcher()) {
        val analytics = FakeAnalytics()
        val consent = FakeConsentManager(UNKNOWN)
        testTracker(analytics = analytics, consent = consent, scope = backgroundScope).start()

        consent.optionalDataConsent.value = GRANTED

        assertThat(analytics.calls).containsExactly(off, Call.Reset, on)
    }

    // A2
    @Test
    fun deniedToGrantedToDeniedResetsEachTimeCollectionGoesOff() = runTest(UnconfinedTestDispatcher()) {
        val analytics = FakeAnalytics()
        val consent = FakeConsentManager(DENIED)
        testTracker(analytics = analytics, consent = consent, scope = backgroundScope).start()

        consent.optionalDataConsent.value = GRANTED
        consent.optionalDataConsent.value = DENIED

        assertThat(analytics.calls).containsExactly(off, Call.Reset, on, off, Call.Reset)
    }

    // AC-10
    @Test
    fun devKeepsCollectionOffAndResetsEvenWhenGranted() = runTest {
        val analytics = FakeAnalytics()
        val tracker = testTracker(
            analytics = analytics,
            consent = FakeConsentManager(GRANTED),
            environment = AppEnvironment.DEV,
            scope = backgroundScope
        )

        tracker.start()

        assertThat(analytics.calls).containsExactly(off, Call.Reset)
    }

    // AC-21
    @Test
    fun aFailingDisableStillResetsAndDoesNotThrow() = runTest(UnconfinedTestDispatcher()) {
        val analytics = FakeAnalytics(throwOn = setOf(Call.SetCollection::class))
        val tracker = testTracker(analytics = analytics, consent = FakeConsentManager(DENIED), scope = backgroundScope)

        tracker.start()

        assertThat(analytics.calls).containsExactly(off, Call.Reset)
    }

    // AC-21
    @Test
    fun aFailingResetDoesNotStopTheCollectorFromFollowingLaterChanges() = runTest(UnconfinedTestDispatcher()) {
        val analytics = FakeAnalytics(throwOn = setOf(Call.Reset::class))
        val consent = FakeConsentManager(DENIED)
        testTracker(analytics = analytics, consent = consent, scope = backgroundScope).start()

        consent.optionalDataConsent.value = GRANTED

        assertThat(analytics.calls).containsExactly(off, Call.Reset, on)
    }
}
