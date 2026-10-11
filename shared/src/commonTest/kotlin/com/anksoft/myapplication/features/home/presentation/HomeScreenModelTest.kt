package com.anksoft.myapplication.features.home.presentation

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.anksoft.myapplication.core.analytics.FakeAnalytics
import com.anksoft.myapplication.core.analytics.testTracker
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.logging.NoOpLogger
import com.anksoft.myapplication.features.consent.FakeConsentRepository
import com.anksoft.myapplication.features.consent.domain.AccountConsentManager
import com.anksoft.myapplication.features.consent.domain.model.AccountConsent
import com.anksoft.myapplication.features.consent.domain.model.ConsentDecision
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class HomeScreenModelTest {

    private lateinit var consent: FakeConsentRepository

    @BeforeTest
    fun setUp() {
        // screenModelScope is Main-dispatched, so Main must be replaced.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        consent = FakeConsentRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createManager() = AccountConsentManager(
        repository = consent,
        scope = CoroutineScope(UnconfinedTestDispatcher()),
        logger = NoOpLogger
    )

    // AC-7
    @Test
    fun anAccountWithoutADecisionGetsThePrompt() = runTest {
        val model = HomeScreenModel(createManager(), testTracker())

        assertThat(model.state.value.showConsentPrompt).isTrue()
    }

    // AC-12
    @Test
    fun thePromptWaitsWhileTheDecisionIsStillLoading() = runTest {
        val gate = CompletableDeferred<Unit>()
        consent.fetchGate = gate
        val model = HomeScreenModel(createManager(), testTracker())

        assertThat(model.state.value.showConsentPrompt).isFalse()

        gate.complete(Unit)

        assertThat(model.state.value.showConsentPrompt).isTrue()
    }

    // AC-12
    @Test
    fun noPromptWhenTheDecisionCouldNotBeLoaded() = runTest {
        consent.fetchResult = Result.Failure(DataError.Remote.NO_INTERNET)

        val model = HomeScreenModel(createManager(), testTracker())

        assertThat(model.state.value.showConsentPrompt).isFalse()
    }

    // AC-10
    @Test
    fun noPromptForAnAccountThatAlreadyDecided() = runTest {
        consent.fetchResult = Result.Success(AccountConsent(ConsentDecision.DENIED, textVersion = 1))
        val deniedModel = HomeScreenModel(createManager(), testTracker())
        consent.fetchResult = Result.Success(AccountConsent(ConsentDecision.GRANTED, textVersion = 1))
        val grantedModel = HomeScreenModel(createManager(), testTracker())

        assertThat(deniedModel.state.value.showConsentPrompt).isFalse()
        assertThat(grantedModel.state.value.showConsentPrompt).isFalse()
    }

    // AC-23
    @Test
    fun aGrantThatNeedsANewConsentGetsThePrompt() = runTest {
        consent.fetchResult = Result.Success(
            AccountConsent(ConsentDecision.GRANTED, textVersion = 1, reconsentRequired = true)
        )

        val model = HomeScreenModel(createManager(), testTracker())

        assertThat(model.state.value.showConsentPrompt).isTrue()
    }

    // AC-10
    @Test
    fun thePromptIsShownOnceEvenWhenTheHomeScreenIsRecreated() = runTest {
        val manager = createManager()
        val first = HomeScreenModel(manager, testTracker())
        assertThat(first.state.value.showConsentPrompt).isTrue()
        first.onEvent(HomeEvent.ConsentPromptOpened)

        val second = HomeScreenModel(manager, testTracker())

        assertThat(first.state.value.showConsentPrompt).isFalse()
        assertThat(second.state.value.showConsentPrompt).isFalse()
    }

    // AC-7
    @Test
    fun openingThePromptClearsTheRequest() = runTest {
        val model = HomeScreenModel(createManager(), testTracker())

        model.onEvent(HomeEvent.ConsentPromptOpened)

        assertThat(model.state.value).isEqualTo(HomeState(showConsentPrompt = false))
    }

    // AC-12, AC-22
    @Test
    fun openingHomeTracksOneScreenView() = runTest {
        val analytics = FakeAnalytics()

        HomeScreenModel(createManager(), testTracker(analytics = analytics))

        assertThat(analytics.events).containsExactly(
            FakeAnalytics.Call.Event(
                "screen_view",
                mapOf("screen_name" to "home", "screen_class" to "home", "language" to "en")
            )
        )
    }

    // AC-12, AC-22
    @Test
    fun consentPromptEventsDoNotTrackHomeAgain() = runTest {
        val analytics = FakeAnalytics()
        val model = HomeScreenModel(createManager(), testTracker(analytics = analytics))
        assertThat(model.state.value.showConsentPrompt).isTrue()

        model.onEvent(HomeEvent.ConsentPromptOpened)

        assertThat(model.state.value.showConsentPrompt).isFalse()
        assertThat(analytics.events).hasSize(1)
    }
}
