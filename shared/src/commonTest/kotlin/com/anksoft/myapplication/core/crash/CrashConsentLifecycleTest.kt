package com.anksoft.myapplication.core.crash

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import com.anksoft.myapplication.core.config.AppEnvironment
import com.anksoft.myapplication.core.consent.FakeConsentManager
import com.anksoft.myapplication.core.consent.OptionalDataConsent
import com.anksoft.myapplication.core.crash.FakeCrashReporter.Call
import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.logging.DispatchingLogger
import com.anksoft.myapplication.core.logging.LogSeverity
import com.anksoft.myapplication.core.logging.NoOpLogger
import com.anksoft.myapplication.core.logging.debug
import com.anksoft.myapplication.core.logging.error
import com.anksoft.myapplication.core.logging.info
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * The real controller, gate, gated reporter, writer and logger with a fake vendor and a fake consent:
 * what actually reaches the vendor while the consent changes at runtime (no restart).
 */
class CrashConsentLifecycleTest {

    private val reporter = FakeCrashReporter()
    private val storage = MapSettings()
    private val gate = CrashGate()

    private val logger: AppLogger = DispatchingLogger(
        LogSeverity.WARN,
        listOf(CrashLogWriter(GatedCrashReporter(reporter, gate)))
    )

    private fun TestScope.start(
        initial: OptionalDataConsent,
        environment: AppEnvironment = AppEnvironment.STAGE,
    ): FakeConsentManager {
        val consent = FakeConsentManager(initial)
        CrashReportingController(
            reporter, gate, consent, InstallIdStore({ storage }), environment, NoOpLogger,
            backgroundScope + UnconfinedTestDispatcher(testScheduler)
        ).start()
        return consent
    }

    private fun failWith(message: String) = logger.error("T", IllegalStateException("secret")) { message }

    // AC-4: fresh install (no decision) and a refusal both keep every record away from the vendor.
    @Test
    fun noDecisionAndDeniedSendNothingToTheVendor() = runTest {
        val consent = start(OptionalDataConsent.UNKNOWN)
        failWith("while unknown")
        consent.optionalDataConsent.value = OptionalDataConsent.DENIED
        failWith("while denied")

        assertThat(reporter.breadcrumbs).isEmpty()
        assertThat(reporter.nonFatals).isEmpty()
        assertThat(reporter.calls.filterIsInstance<Call.SetId>().filter { it.id != null }).isEmpty()
    }

    // AC-6, AC-8: events from before the grant are never delivered; later ones are, without a restart.
    @Test
    fun onlyEventsAfterTheGrantReachTheVendor() = runTest {
        val consent = start(OptionalDataConsent.DENIED)
        failWith("before grant")

        consent.optionalDataConsent.value = OptionalDataConsent.GRANTED
        failWith("after grant")

        assertThat(reporter.nonFatals.map { it.message }).isEqualTo(listOf("after grant"))
        assertThat(reporter.breadcrumbs.joinToString()).doesNotContain("before grant")
        assertThat(reporter.breadcrumbs.joinToString()).contains("after grant")
    }

    // AC-7: after a withdrawal nothing more is delivered, without a restart.
    @Test
    fun nothingIsDeliveredAfterTheConsentIsWithdrawn() = runTest {
        val consent = start(OptionalDataConsent.GRANTED)
        failWith("while granted")
        reporter.clear()

        consent.optionalDataConsent.value = OptionalDataConsent.DENIED
        reporter.clear()
        failWith("after withdrawal")
        logger.info("T") { "info after withdrawal" }

        assertThat(reporter.calls).isEmpty()
    }

    // AC-10: dev never delivers, even when the consent is granted; the pipeline stays closed.
    @Test
    fun devDeliversNothingEvenWhenGranted() = runTest {
        start(OptionalDataConsent.GRANTED, AppEnvironment.DEV)
        reporter.clear()

        failWith("in dev")

        assertThat(reporter.calls).isEmpty()
    }

    // AC-11, AC-12, AC-13: stage and prod deliver an ERROR with a throwable as a non-fatal plus breadcrumbs.
    @Test
    fun stageAndProdDeliverNonFatalAndBreadcrumbsButNoDebug() = runTest {
        for (environment in listOf(AppEnvironment.STAGE, AppEnvironment.PROD)) {
            reporter.clear()
            gate.close()
            start(OptionalDataConsent.GRANTED, environment)
            assertThat(reporter.calls.contains(Call.CustomKey("environment", environment.name))).isEqualTo(true)
            reporter.clear()

            logger.debug("T") { "debug line" }
            logger.info("T") { "info line" }
            failWith("error line")

            assertThat(reporter.nonFatals.map { it.message }).isEqualTo(listOf("error line"))
            assertThat(reporter.breadcrumbs).isNotEmpty()
            assertThat(reporter.breadcrumbs.joinToString()).doesNotContain("debug line")
            assertThat(reporter.breadcrumbs.joinToString()).contains("info line")
        }
    }

    // AC-16: the only identifier handed to the vendor is the random install id.
    @Test
    fun theOnlyIdentifierSentIsTheRandomInstallId() = runTest {
        start(OptionalDataConsent.GRANTED)

        val ids = reporter.calls.filterIsInstance<Call.SetId>().mapNotNull { it.id }
        val storedId = storage.getStringOrNull(INSTALL_ID_KEY)
        assertThat(ids).isEqualTo(listOf(storedId!!))
        assertThat(reporter.calls.filterIsInstance<Call.CustomKey>().map { it.key }).isEqualTo(listOf("environment"))
    }
}
