package com.anksoft.myapplication.core.crash

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.anksoft.myapplication.core.config.AppEnvironment
import com.anksoft.myapplication.core.consent.FakeConsentManager
import com.anksoft.myapplication.core.consent.OptionalDataConsent
import com.anksoft.myapplication.core.crash.FakeCrashReporter.Call
import com.anksoft.myapplication.core.logging.NoOpLogger
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.fail

class CrashReportingControllerTest {

    private val reporter = FakeCrashReporter()
    private val gate = CrashGate()
    private val storage = MapSettings()
    private val installIds = InstallIdStore({ storage })

    private fun controller(
        consent: FakeConsentManager,
        scope: CoroutineScope,
        environment: AppEnvironment = AppEnvironment.STAGE,
        vendor: CrashReporter? = reporter,
        ids: InstallIdStore = installIds,
    ) = CrashReportingController(vendor, gate, consent, ids, environment, NoOpLogger, scope)

    private fun TestScope.started(
        initial: OptionalDataConsent,
        environment: AppEnvironment = AppEnvironment.STAGE,
    ): Pair<CrashReportingController, FakeConsentManager> {
        val consent = FakeConsentManager(initial)
        val controller = controller(consent, backgroundScope + UnconfinedTestDispatcher(testScheduler), environment)
        controller.start()
        return controller to consent
    }

    // AC-4, AC-6
    @Test
    fun startPurgesUnsentReportsBeforeAnythingElse() = runTest {
        started(OptionalDataConsent.GRANTED)

        assertThat(reporter.calls.first()).isEqualTo(Call.DeleteUnsent)
    }

    // AC-9
    @Test
    fun unknownAtStartKeepsGateClosedAndCollectionOff() = runTest {
        started(OptionalDataConsent.UNKNOWN)

        assertThat(gate.isOpen).isFalse()
        assertThat(reporter.calls).isEqualTo(listOf(Call.DeleteUnsent, Call.SetCollection(false)))
        assertThat(storage.keys).isEmpty()
    }

    // AC-5, AC-11, AC-16
    @Test
    fun grantedInStageEnablesCollectionSetsAnonymousIdAndEnvironmentKeyThenOpensGate() = runTest {
        started(OptionalDataConsent.GRANTED)

        val id = storage.getStringOrNull(INSTALL_ID_KEY)
        assertThat(id).isNotNull()
        assertThat(reporter.calls).isEqualTo(
            listOf(
                Call.DeleteUnsent,
                Call.CustomKey("environment", "STAGE"),
                Call.SetId(id),
                Call.SetCollection(true),
            )
        )
        assertThat(gate.isOpen).isTrue()
    }

    // AC-11
    @Test
    fun grantedInProdBehavesLikeStage() = runTest {
        started(OptionalDataConsent.GRANTED, AppEnvironment.PROD)

        assertThat(reporter.calls.contains(Call.CustomKey("environment", "PROD"))).isTrue()
        assertThat(reporter.calls.contains(Call.SetCollection(true))).isTrue()
        assertThat(gate.isOpen).isTrue()
    }

    // AC-10
    @Test
    fun devNeverOpensEvenWhenGranted() = runTest {
        started(OptionalDataConsent.GRANTED, AppEnvironment.DEV)

        assertThat(gate.isOpen).isFalse()
        assertThat(reporter.calls).isEqualTo(listOf(Call.DeleteUnsent, Call.SetCollection(false)))
        assertThat(storage.keys).isEmpty()
    }

    // AC-7: the gate closes before the flag, then unsent reports and both identifiers go.
    @Test
    fun deniedAfterGrantedClosesGateFirstThenDisablesDeletesUnsentAndResetsId() = runTest {
        var gateOpenWhenFlagTurnedOff: Boolean? = null
        val watching = object : CrashReporter by reporter {
            override fun setCollectionEnabled(enabled: Boolean) {
                if (!enabled) gateOpenWhenFlagTurnedOff = gate.isOpen
                reporter.setCollectionEnabled(enabled)
            }
        }
        val consent = FakeConsentManager(OptionalDataConsent.GRANTED)
        controller(consent, backgroundScope + UnconfinedTestDispatcher(testScheduler), vendor = watching).start()
        assertThat(storage.getStringOrNull(INSTALL_ID_KEY)).isNotNull()
        reporter.clear()

        consent.optionalDataConsent.value = OptionalDataConsent.DENIED

        assertThat(gateOpenWhenFlagTurnedOff).isEqualTo(false)
        assertThat(gate.isOpen).isFalse()
        assertThat(reporter.calls).isEqualTo(
            listOf(
                Call.SetCollection(false),
                Call.DeleteUnsent,
                Call.SetId(null),
                Call.DeleteVendorId,
            )
        )
        assertThat(storage.getStringOrNull(INSTALL_ID_KEY)).isEqualTo(null)
    }

    // AC-8, AC-17
    @Test
    fun grantedAfterDeniedReopensWithoutRestartAndWithANewId() = runTest {
        val (_, consent) = started(OptionalDataConsent.GRANTED)
        val firstId = storage.getStringOrNull(INSTALL_ID_KEY)
        consent.optionalDataConsent.value = OptionalDataConsent.DENIED
        reporter.clear()

        consent.optionalDataConsent.value = OptionalDataConsent.GRANTED

        val secondId = storage.getStringOrNull(INSTALL_ID_KEY)
        assertThat(secondId).isNotNull()
        assertThat(secondId).isNotEqualTo(firstId)
        assertThat(reporter.calls).isEqualTo(
            listOf(
                Call.CustomKey("environment", "STAGE"),
                Call.SetId(secondId),
                Call.SetCollection(true),
            )
        )
        assertThat(gate.isOpen).isTrue()
    }

    // AC-17: signing out and in again passes through UNKNOWN and keeps the id.
    @Test
    fun unknownAfterGrantedKeepsTheSameIdForTheNextGrant() = runTest {
        val (_, consent) = started(OptionalDataConsent.GRANTED)
        val firstId = storage.getStringOrNull(INSTALL_ID_KEY)

        consent.optionalDataConsent.value = OptionalDataConsent.UNKNOWN
        assertThat(gate.isOpen).isFalse()
        assertThat(reporter.calls.last()).isEqualTo(Call.SetCollection(false))
        consent.optionalDataConsent.value = OptionalDataConsent.GRANTED

        assertThat(storage.getStringOrNull(INSTALL_ID_KEY)).isEqualTo(firstId)
        assertThat(reporter.calls.last()).isEqualTo(Call.SetCollection(true))
        assertThat(gate.isOpen).isTrue()
    }

    @Test
    fun deniedAtStartIsAppliedAgainIdempotently() = runTest {
        started(OptionalDataConsent.DENIED)
        val afterFirstStart = reporter.calls.toList()

        // A second launch with the same state does the same work again and ends in the same place.
        val secondGate = CrashGate()
        val secondReporter = FakeCrashReporter()
        CrashReportingController(
            secondReporter, secondGate, FakeConsentManager(OptionalDataConsent.DENIED), installIds,
            AppEnvironment.STAGE, NoOpLogger, backgroundScope + UnconfinedTestDispatcher(testScheduler)
        ).start()

        assertThat(secondReporter.calls).isEqualTo(afterFirstStart)
        assertThat(secondGate.isOpen).isFalse()
        assertThat(afterFirstStart).isEqualTo(
            listOf(
                Call.DeleteUnsent,
                Call.SetCollection(false),
                Call.DeleteUnsent,
                Call.SetId(null),
                Call.DeleteVendorId,
            )
        )
    }

    // AC-2, AC-3
    @Test
    fun noVendorBoundLeavesControllerIdleAndNeverTouchesStorage() = runTest {
        val untouchable = InstallIdStore({ fail("storage must not be resolved without a vendor") })
        val consent = FakeConsentManager(OptionalDataConsent.GRANTED)

        val idle = controller(
            consent, backgroundScope + UnconfinedTestDispatcher(testScheduler), vendor = null, ids = untouchable
        )
        idle.start()
        consent.optionalDataConsent.value = OptionalDataConsent.DENIED
        consent.optionalDataConsent.value = OptionalDataConsent.GRANTED

        assertThat(gate.isOpen).isFalse()
        assertThat(reporter.calls).isEmpty()
    }

    @Test
    fun throwingReporterDoesNotCrashAndGateEndsInTheRightState() = runTest {
        reporter.throwOn = setOf(
            Call.SetCollection::class, Call.DeleteUnsent::class, Call.SetId::class,
            Call.CustomKey::class, Call.DeleteVendorId::class,
        )
        val (_, consent) = started(OptionalDataConsent.GRANTED)
        assertThat(gate.isOpen).isTrue()

        consent.optionalDataConsent.value = OptionalDataConsent.DENIED

        assertThat(gate.isOpen).isFalse()
        assertThat(storage.getStringOrNull(INSTALL_ID_KEY)).isEqualTo(null)
    }

    @Test
    fun startTwiceIsANoOp() = runTest {
        val consent = FakeConsentManager(OptionalDataConsent.GRANTED)
        val controller = controller(consent, backgroundScope + UnconfinedTestDispatcher(testScheduler))

        controller.start()
        val afterFirst = reporter.calls.toList()
        controller.start()

        assertThat(reporter.calls).isEqualTo(afterFirst)
    }
}
