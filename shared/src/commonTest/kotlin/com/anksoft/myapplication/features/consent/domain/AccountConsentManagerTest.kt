package com.anksoft.myapplication.features.consent.domain

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.anksoft.myapplication.core.consent.OptionalDataConsent
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.logging.NoOpLogger
import com.anksoft.myapplication.core.logging.recordingLogger
import com.anksoft.myapplication.core.session.SignOutReason
import com.anksoft.myapplication.features.consent.FakeConsentRepository
import com.anksoft.myapplication.features.consent.domain.model.AccountConsent
import com.anksoft.myapplication.features.consent.domain.model.ConsentChoice
import com.anksoft.myapplication.features.consent.domain.model.ConsentDecision
import com.anksoft.myapplication.features.consent.domain.model.ConsentStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class AccountConsentManagerTest {

    private val repository = FakeConsentRepository()
    private val timeSource = TestTimeSource()

    private val none = AccountConsent(ConsentDecision.NONE, textVersion = null)
    private val granted = AccountConsent(ConsentDecision.GRANTED, textVersion = 1)
    private val denied = AccountConsent(ConsentDecision.DENIED, textVersion = 1)

    private fun TestScope.createManager() = AccountConsentManager(
        repository = repository,
        scope = backgroundScope,
        timeSource = timeSource,
        logger = NoOpLogger
    )

    private fun choice(granted: Boolean) = ConsentChoice(granted = granted, textVersion = 1, textLanguage = "en")

    // region start-up

    // AC-19
    @Test
    fun withoutASessionTheFacadeSaysUnknownAndNothingIsFetched() = runTest {
        repository.signedIn = false

        val manager = createManager()
        runCurrent()

        assertThat(manager.status.value).isEqualTo(ConsentStatus.SignedOut)
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.UNKNOWN)
        assertThat(repository.fetchCount).isEqualTo(0)
    }

    // AC-6, AC-27
    @Test
    fun aCachedGrantAppliesImmediatelyAtColdStartBeforeAnyNetworkAnswer() = runTest {
        repository.cached = granted
        repository.fetchGate = CompletableDeferred()

        val manager = createManager()

        // Not even runCurrent(): the first value must be available without suspending.
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.GRANTED)
        assertThat(manager.status.value).isEqualTo(ConsentStatus.Granted())
    }

    // AC-6
    @Test
    fun withoutACacheTheStateIsLoadingUntilTheFetchAnswers() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.fetchGate = gate
        repository.fetchResult = Result.Success(granted)

        val manager = createManager()
        runCurrent()

        assertThat(manager.status.value).isEqualTo(ConsentStatus.Loading)
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.UNKNOWN)

        gate.complete(Unit)
        runCurrent()

        assertThat(manager.status.value).isEqualTo(ConsentStatus.Granted())
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.GRANTED)
    }

    // AC-12, AC-25
    @Test
    fun aFailedFirstFetchLeavesTheStateUnavailableAndTheFacadeOff() = runTest {
        repository.fetchResult = Result.Failure(DataError.Remote.NO_INTERNET)

        val manager = createManager()
        runCurrent()

        assertThat(manager.status.value).isEqualTo(ConsentStatus.Unavailable)
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.UNKNOWN)
    }

    // AC-12
    @Test
    fun aFailedRefreshKeepsTheCachedDecision() = runTest {
        repository.cached = granted
        repository.fetchResult = Result.Failure(DataError.Remote.REQUEST_TIMEOUT)

        val manager = createManager()
        runCurrent()

        assertThat(manager.status.value).isEqualTo(ConsentStatus.Granted())
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.GRANTED)
    }

    // AC-25
    @Test
    fun anAccountThatNeverDecidedIsUnknown() = runTest {
        repository.fetchResult = Result.Success(none)

        val manager = createManager()
        runCurrent()

        assertThat(manager.status.value).isEqualTo(ConsentStatus.Undecided)
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.UNKNOWN)
    }

    // AC-25, AC-9
    @Test
    fun aDeniedDecisionIsDenied() = runTest {
        repository.fetchResult = Result.Success(denied)

        val manager = createManager()
        runCurrent()

        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.DENIED)
    }

    // AC-23
    @Test
    fun aGrantThatNeedsReconsentIsUnknownAndAsksForThePrompt() = runTest {
        repository.fetchResult = Result.Success(granted.copy(reconsentRequired = true))

        val manager = createManager()
        runCurrent()

        assertThat(manager.status.value).isEqualTo(ConsentStatus.Granted(reconsentRequired = true))
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.UNKNOWN)
        assertThat(manager.claimPrompt()).isTrue()
    }

    // endregion

    // region session changes

    // AC-18, AC-19
    @Test
    fun signingOutSwitchesTheFacadeToUnknownAtOnce() = runTest {
        repository.cached = granted
        val manager = createManager()

        manager.onSignedOut(SignOutReason.USER_LOGOUT)

        // No runCurrent(): the facade must already be off when the call returns.
        assertThat(manager.status.value).isEqualTo(ConsentStatus.SignedOut)
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.UNKNOWN)
    }

    // AC-28
    @Test
    fun anExpiredSessionSwitchesTheFacadeToUnknown() = runTest {
        repository.cached = granted
        val manager = createManager()

        manager.onSignedOut(SignOutReason.EXPIRED)

        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.UNKNOWN)
    }

    // AC-26, E5 rules 1-4
    @Test
    fun theFacadeGoesThroughUnknownNeverDeniedWhenTheAccountChanges() = runTest {
        repository.cached = granted
        val manager = createManager()

        manager.optionalDataConsent.test {
            assertThat(awaitItem()).isEqualTo(OptionalDataConsent.GRANTED)

            manager.onSignedOut(SignOutReason.USER_LOGOUT)
            assertThat(awaitItem()).isEqualTo(OptionalDataConsent.UNKNOWN)

            // Another account signs in; its decision is "denied" once the fetch answers.
            repository.cached = null
            repository.fetchResult = Result.Success(denied)
            manager.onSignedIn()
            runCurrent()
            assertThat(awaitItem()).isEqualTo(OptionalDataConsent.DENIED)
        }
    }

    // AC-6
    @Test
    fun signingInWithoutACachedDecisionFetchesTheAccountsDecision() = runTest {
        repository.signedIn = false
        val manager = createManager()
        repository.signedIn = true
        repository.fetchResult = Result.Success(granted)

        manager.onSignedIn()
        runCurrent()

        assertThat(repository.fetchCount).isEqualTo(1)
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.GRANTED)
    }

    // AC-3
    @Test
    fun signingInWithTheDecisionFromTheRegisterResponseNeedsNoFetch() = runTest {
        repository.signedIn = false
        val manager = createManager()
        repository.signedIn = true
        repository.cached = denied

        manager.onSignedIn()
        runCurrent()

        assertThat(repository.fetchCount).isEqualTo(0)
        assertThat(manager.status.value).isEqualTo(ConsentStatus.Denied)
        assertThat(manager.claimPrompt()).isFalse()
    }

    // AC-18
    @Test
    fun anAnswerForAnEarlierAccountIsIgnoredByTheNextOne() = runTest {
        repository.ignoreCancellation = true
        val gateOfAccountA = CompletableDeferred<Unit>()
        repository.fetchGate = gateOfAccountA
        repository.fetchResult = Result.Success(granted)
        val manager = createManager()
        runCurrent()

        // Account A signs out while its fetch is on the wire; account B signs in and is denied.
        manager.onSignedOut(SignOutReason.USER_LOGOUT)
        repository.fetchGate = null
        repository.fetchResult = Result.Success(denied)
        manager.onSignedIn()
        runCurrent()
        assertThat(manager.status.value).isEqualTo(ConsentStatus.Denied)

        gateOfAccountA.complete(Unit)
        runCurrent()

        assertThat(manager.status.value).isEqualTo(ConsentStatus.Denied)
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.DENIED)
    }

    // AC-18
    @Test
    fun aFetchInFlightWhenTheUserSignsOutIsCancelled() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.fetchGate = gate
        repository.fetchResult = Result.Success(granted)
        val manager = createManager()
        runCurrent()

        manager.onSignedOut(SignOutReason.USER_LOGOUT)
        gate.complete(Unit)
        runCurrent()

        assertThat(manager.status.value).isEqualTo(ConsentStatus.SignedOut)
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.UNKNOWN)
    }

    // AC-18
    @Test
    fun aSaveResultForAnEarlierAccountIsNotApplied() = runTest {
        repository.cached = granted
        val manager = createManager()
        val gate = CompletableDeferred<Unit>()
        repository.saveGate = gate
        val saving = async { manager.decide(choice(granted = false)) }
        runCurrent()

        manager.onSignedOut(SignOutReason.USER_LOGOUT)
        gate.complete(Unit)
        runCurrent()
        saving.await()

        assertThat(manager.status.value).isEqualTo(ConsentStatus.SignedOut)
    }

    // endregion

    // region changing the decision

    // AC-15
    @Test
    fun switchingCollectionOffTakesEffectBeforeTheBackendAnswers() = runTest {
        repository.cached = granted
        val manager = createManager()
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        repository.saveGate = gate

        val saving = async { manager.decide(choice(granted = false)) }
        runCurrent()

        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.DENIED)
        assertThat(manager.status.value).isEqualTo(ConsentStatus.Granted(suppressed = true))

        gate.complete(Unit)
        runCurrent()

        assertThat(saving.await()).isEqualTo(Result.Success(Unit))
        assertThat(manager.status.value).isEqualTo(ConsentStatus.Denied)
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.DENIED)
        assertThat(repository.savedChoices).isEqualTo(listOf(choice(granted = false)))
    }

    // AC-16, A3
    @Test
    fun aFailedSwitchOffKeepsCollectionOffWhileTheScreenShowsTheStoredGrant() = runTest {
        repository.cached = granted
        val manager = createManager()
        runCurrent()
        repository.saveResult = Result.Failure(DataError.Remote.NO_INTERNET)

        val result = manager.decide(choice(granted = false))

        assertThat(result).isEqualTo(Result.Failure(DataError.Remote.NO_INTERNET))
        assertThat(manager.status.value).isEqualTo(ConsentStatus.Granted(suppressed = true))
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.DENIED)
    }

    // A3
    @Test
    fun theNextSuccessfulFetchLiftsASuppressionAfterAFailedSwitchOff() = runTest {
        repository.cached = granted
        repository.fetchResult = Result.Success(granted)
        val manager = createManager()
        runCurrent()
        repository.saveResult = Result.Failure(DataError.Remote.SERVER_ERROR)
        manager.decide(choice(granted = false))

        manager.refresh()
        runCurrent()

        assertThat(manager.status.value).isEqualTo(ConsentStatus.Granted())
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.GRANTED)
    }

    // AC-15
    @Test
    fun switchingCollectionOnWaitsForTheBackend() = runTest {
        repository.fetchResult = Result.Success(denied)
        val manager = createManager()
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        repository.saveGate = gate

        val saving = async { manager.decide(choice(granted = true)) }
        runCurrent()

        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.DENIED)

        gate.complete(Unit)
        runCurrent()
        saving.await()

        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.GRANTED)
    }

    // AC-8, AC-11
    @Test
    fun acceptingThePromptStaysUnknownUntilTheBackendConfirms() = runTest {
        repository.fetchResult = Result.Success(none)
        val manager = createManager()
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        repository.saveGate = gate

        val saving = async { manager.decide(choice(granted = true)) }
        runCurrent()

        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.UNKNOWN)

        gate.complete(Unit)
        runCurrent()
        saving.await()

        assertThat(manager.status.value).isEqualTo(ConsentStatus.Granted())
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.GRANTED)
    }

    // AC-11, AC-16
    @Test
    fun aFailedSwitchOnLeavesTheStateAsItWas() = runTest {
        repository.fetchResult = Result.Success(none)
        val manager = createManager()
        runCurrent()
        repository.saveResult = Result.Failure(DataError.Remote.NO_INTERNET)

        val result = manager.decide(choice(granted = true))

        assertThat(result).isInstanceOf(Result.Failure::class)
        assertThat(manager.status.value).isEqualTo(ConsentStatus.Undecided)
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.UNKNOWN)
    }

    // AC-22
    @Test
    fun aRefusedVersionIsReportedToTheCaller() = runTest {
        repository.fetchResult = Result.Success(none)
        val manager = createManager()
        runCurrent()
        repository.saveResult = Result.Failure(DataError.Remote.UNPROCESSABLE)

        val result = manager.decide(choice(granted = true))

        assertThat(result).isEqualTo(Result.Failure(DataError.Remote.UNPROCESSABLE))
    }

    // AC-17
    @Test
    fun aFetchThatStartedBeforeADecisionCannotOverwriteIt() = runTest {
        // The cached grant is old; the fetch for it is still on the wire when the user switches off.
        repository.cached = granted
        repository.fetchResult = Result.Success(granted)
        val fetchGate = CompletableDeferred<Unit>()
        repository.fetchGate = fetchGate
        val manager = createManager()
        runCurrent()

        manager.decide(choice(granted = false))
        assertThat(manager.status.value).isEqualTo(ConsentStatus.Denied)
        fetchGate.complete(Unit)
        runCurrent()

        assertThat(manager.status.value).isEqualTo(ConsentStatus.Denied)
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.DENIED)
    }

    // AC-15, AC-16: leaving the screen while a switch-off is on its way must not lose the write.
    @Test
    fun aSwitchOffIsStillSavedWhenTheCallerLeavesBeforeTheBackendAnswers() = runTest {
        repository.cached = granted
        val manager = createManager()
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        repository.saveGate = gate
        val saving = launch { manager.decide(choice(granted = false)) }
        runCurrent()

        saving.cancel()
        gate.complete(Unit)
        runCurrent()

        assertThat(manager.status.value).isEqualTo(ConsentStatus.Denied)
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.DENIED)
        assertThat(repository.savedChoices).isEqualTo(listOf(choice(granted = false)))
    }

    // AC-8, AC-15
    @Test
    fun aSwitchOnIsStillSavedWhenTheCallerLeavesBeforeTheBackendAnswers() = runTest {
        repository.fetchResult = Result.Success(none)
        val manager = createManager()
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        repository.saveGate = gate
        val saving = launch { manager.decide(choice(granted = true)) }
        runCurrent()

        saving.cancel()
        gate.complete(Unit)
        runCurrent()

        assertThat(manager.status.value).isEqualTo(ConsentStatus.Granted())
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.GRANTED)
        assertThat(repository.savedChoices).isEqualTo(listOf(choice(granted = true)))
    }

    // AC-17: a fetch that starts while the write runs and ends after it carries the old value.
    @Test
    fun aFetchThatStartedWhileTheDecisionWasBeingSavedCannotOverwriteIt() = runTest {
        repository.fetchResult = Result.Success(none)
        val manager = createManager()
        runCurrent()
        val saveGate = CompletableDeferred<Unit>()
        val fetchGate = CompletableDeferred<Unit>()
        repository.saveGate = saveGate
        repository.fetchGate = fetchGate
        val saving = launch { manager.decide(choice(granted = true)) }
        runCurrent()

        manager.refresh()
        runCurrent()
        saveGate.complete(Unit)
        runCurrent()
        assertThat(manager.status.value).isEqualTo(ConsentStatus.Granted())
        fetchGate.complete(Unit)
        runCurrent()
        saving.join()

        assertThat(manager.status.value).isEqualTo(ConsentStatus.Granted())
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.GRANTED)
    }

    @Test
    fun decisionsAreSentOneAtATime() = runTest {
        repository.fetchResult = Result.Success(none)
        val manager = createManager()
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        repository.saveGate = gate

        val first = async { manager.decide(choice(granted = true)) }
        val second = async { manager.decide(choice(granted = false)) }
        runCurrent()

        assertThat(repository.savedChoices).hasSize(1)

        gate.complete(Unit)
        runCurrent()
        first.await()
        second.await()

        assertThat(repository.savedChoices)
            .isEqualTo(listOf(choice(granted = true), choice(granted = false)))
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.DENIED)
    }

    // endregion

    // region prompt

    // AC-7, AC-10
    @Test
    fun thePromptIsClaimedOnceAndNotAgainInTheSameSession() = runTest {
        repository.fetchResult = Result.Success(none)
        val manager = createManager()
        runCurrent()

        assertThat(manager.claimPrompt()).isTrue()
        assertThat(manager.claimPrompt()).isFalse()
    }

    // AC-10
    @Test
    fun thePromptIsDueAgainAfterTheNextSignIn() = runTest {
        repository.fetchResult = Result.Success(none)
        val manager = createManager()
        runCurrent()
        manager.claimPrompt()

        manager.onSignedOut(SignOutReason.USER_LOGOUT)
        manager.onSignedIn()
        runCurrent()

        assertThat(manager.claimPrompt()).isTrue()
    }

    // AC-12
    @Test
    fun noPromptWhileTheDecisionIsLoadingOrCouldNotBeRead() = runTest {
        repository.fetchResult = Result.Failure(DataError.Remote.NO_INTERNET)
        val gate = CompletableDeferred<Unit>()
        repository.fetchGate = gate
        val manager = createManager()
        runCurrent()
        assertThat(manager.claimPrompt()).isFalse()

        gate.complete(Unit)
        runCurrent()

        assertThat(manager.status.value).isEqualTo(ConsentStatus.Unavailable)
        assertThat(manager.claimPrompt()).isFalse()
    }

    // AC-9
    @Test
    fun noPromptAfterADecision() = runTest {
        repository.fetchResult = Result.Success(denied)
        val manager = createManager()
        runCurrent()

        assertThat(manager.claimPrompt()).isFalse()
    }

    // endregion

    // region refreshing

    @Test
    fun refreshWhileAFetchIsRunningDoesNotStartAnother() = runTest {
        repository.fetchGate = CompletableDeferred()
        val manager = createManager()
        runCurrent()

        manager.refresh()
        manager.refresh()
        runCurrent()

        assertThat(repository.fetchCount).isEqualTo(1)
    }

    // AC-19
    @Test
    fun refreshWithoutASessionDoesNothing() = runTest {
        repository.signedIn = false
        val manager = createManager()

        manager.refresh()
        runCurrent()

        assertThat(repository.fetchCount).isEqualTo(0)
    }

    // AC-12, AC-17
    @Test
    fun comingToTheForegroundRefreshesAfterAMinuteButNotSooner() = runTest {
        repository.fetchResult = Result.Success(granted)
        val manager = createManager()
        runCurrent()
        assertThat(repository.fetchCount).isEqualTo(1)

        timeSource += 59.seconds
        manager.onAppResumed()
        runCurrent()
        assertThat(repository.fetchCount).isEqualTo(1)

        timeSource += 2.seconds
        manager.onAppResumed()
        runCurrent()
        assertThat(repository.fetchCount).isEqualTo(2)
    }

    // AC-12
    @Test
    fun comingToTheForegroundRetriesAtOnceWhenTheDecisionCouldNotBeRead() = runTest {
        repository.fetchResult = Result.Failure(DataError.Remote.NO_INTERNET)
        val manager = createManager()
        runCurrent()
        repository.fetchResult = Result.Success(granted)

        manager.onAppResumed()
        runCurrent()

        assertThat(repository.fetchCount).isEqualTo(2)
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.GRANTED)
    }

    // AC-19
    @Test
    fun comingToTheForegroundWithoutASessionDoesNothing() = runTest {
        repository.signedIn = false
        val manager = createManager()

        manager.onAppResumed()
        runCurrent()

        assertThat(repository.fetchCount).isEqualTo(0)
    }

    // endregion

    // AC-27
    @Test
    fun logsNeverContainTheDecision() = runTest {
        val (logger, writer) = recordingLogger()
        repository.fetchResult = Result.Success(granted)
        val manager = AccountConsentManager(repository, backgroundScope, timeSource, logger)
        runCurrent()
        manager.decide(choice(granted = false))

        val all = writer.entries.joinToString { it.message }.lowercase()
        assertThat(all).doesNotContain("granted")
        assertThat(all).doesNotContain("denied")
    }
}
