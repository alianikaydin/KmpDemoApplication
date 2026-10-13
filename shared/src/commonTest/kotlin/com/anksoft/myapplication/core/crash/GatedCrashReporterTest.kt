package com.anksoft.myapplication.core.crash

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlin.test.Test

class GatedCrashReporterTest {

    private val delegate = FakeCrashReporter()
    private val gate = CrashGate()
    private val reporter = GatedCrashReporter(delegate, gate)
    private val failure = IllegalStateException("boom")

    // AC-4
    @Test
    fun closedGateDropsBreadcrumbsAndNonFatals() {
        reporter.addBreadcrumb("I/App: hello")
        reporter.recordNonFatal(NonFatalReport.from(failure, "failed"))

        assertThat(delegate.calls).isEmpty()
    }

    @Test
    fun openGatePassesThem() {
        gate.open()

        reporter.addBreadcrumb("I/App: hello")
        reporter.recordNonFatal(NonFatalReport.from(failure, "failed"))

        assertThat(delegate.breadcrumbs).isEqualTo(listOf("I/App: hello"))
        assertThat(delegate.nonFatals.single().message).isEqualTo("failed")
    }

    @Test
    fun gateIsClosedUntilOpenedAndClosedAgainOnDemand() {
        assertThat(gate.isOpen).isEqualTo(false)
        gate.open()
        assertThat(gate.isOpen).isEqualTo(true)
        gate.close()
        assertThat(gate.isOpen).isEqualTo(false)
    }

    @Test
    fun controlCallsPassEvenWhenClosed() {
        reporter.setCollectionEnabled(false)
        reporter.deleteUnsentReports()
        reporter.deleteVendorInstallationId()
        reporter.setAnonymousId(null)
        reporter.setCustomKey("environment", "STAGE")

        assertThat(delegate.calls).isEqualTo(
            listOf(
                FakeCrashReporter.Call.SetCollection(false),
                FakeCrashReporter.Call.DeleteUnsent,
                FakeCrashReporter.Call.DeleteVendorId,
                FakeCrashReporter.Call.SetId(null),
                FakeCrashReporter.Call.CustomKey("environment", "STAGE"),
            )
        )
    }

    // AC-14
    @Test
    fun jwtBearerPasswordTokenAndEmailAreMaskedInBreadcrumbAndNonFatalMessage() {
        gate.open()
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2lnbmF0dXJl"
        val text = "jwt=$jwt Bearer abc.def-123 password=hunter2 id_token: tok-999 mail user@example.com"

        reporter.addBreadcrumb(text)
        reporter.recordNonFatal(NonFatalReport.from(failure, text))

        val sent = listOf(delegate.breadcrumbs.single(), delegate.nonFatals.single().message)
        sent.forEach { out ->
            assertThat(out).doesNotContain(jwt)
            assertThat(out).doesNotContain("abc.def-123")
            assertThat(out).doesNotContain("hunter2")
            assertThat(out).doesNotContain("tok-999")
            assertThat(out).doesNotContain("user@example.com")
            assertThat(out).contains("***")
        }
    }
}
