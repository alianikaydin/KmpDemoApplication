package com.anksoft.myapplication.core.crash

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import kotlin.test.Test

class IosCrashReporterTest {

    private class RecordedError(
        val name: String,
        val reason: String,
        val addresses: List<Long>,
        val fatal: Boolean,
    )

    private class FakeBridge : NativeCrashBridge {
        val userIds = mutableListOf<String>()
        val errors = mutableListOf<RecordedError>()

        override fun setCollectionEnabled(enabled: Boolean) = Unit
        override fun deleteUnsentReports() = Unit
        override fun deleteInstallationId() = Unit
        override fun setUserId(id: String) {
            userIds += id
        }
        override fun setCustomValue(key: String, value: String) = Unit
        override fun log(message: String) = Unit
        override fun recordError(name: String, reason: String, stackAddresses: List<Long>, fatal: Boolean) {
            errors += RecordedError(name, reason, stackAddresses, fatal)
        }
    }

    private val bridge = FakeBridge()
    private val reporter = IosCrashReporter(bridge)

    // AC-18
    @Test
    fun recordNonFatalSendsTypeReasonAndNonEmptyAddresses() {
        val failure = IllegalStateException("boom", RuntimeException("inner"))

        reporter.recordNonFatal(NonFatalReport.from(failure, "Remote failure error=UNKNOWN path=auth/login status=-"))

        val error = bridge.errors.single()
        assertThat(error.name).isEqualTo("IllegalStateException")
        assertThat(error.reason)
            .isEqualTo("Remote failure error=UNKNOWN path=auth/login status=- | causes: RuntimeException")
        assertThat(error.addresses).isNotEmpty()
        assertThat(error.fatal).isEqualTo(false)
    }

    // AC-14
    @Test
    fun reasonNeverContainsTheThrowableMessage() {
        val failure = IllegalStateException(
            "alice@example.com token=abc123",
            RuntimeException("https://secret.host/path")
        )

        reporter.recordNonFatal(NonFatalReport.from(failure, "failed"))
        reporter.recordUncaught(failure)

        bridge.errors.forEach { error ->
            listOf(error.name, error.reason).forEach { text ->
                assertThat(text).doesNotContain("alice@example.com")
                assertThat(text).doesNotContain("abc123")
                assertThat(text).doesNotContain("secret.host")
            }
        }
    }

    @Test
    fun uncaughtErrorIsMarkedFatalWithAFixedReason() {
        reporter.recordUncaught(IllegalStateException("boom"))

        val error = bridge.errors.single()
        assertThat(error.fatal).isEqualTo(true)
        assertThat(error.reason).contains("Uncaught Kotlin exception")
    }

    @Test
    fun nullAnonymousIdClearsTheUserId() {
        reporter.setAnonymousId("abc")
        reporter.setAnonymousId(null)

        assertThat(bridge.userIds).isEqualTo(listOf("abc", ""))
    }
}
