package com.anksoft.myapplication.core.crash

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isSameInstanceAs
import com.anksoft.myapplication.core.logging.LogEntry
import com.anksoft.myapplication.core.logging.LogSeverity
import com.anksoft.myapplication.core.logging.LogTags
import kotlin.test.Test

class CrashLogWriterTest {

    private val reporter = FakeCrashReporter()
    private val writer = CrashLogWriter(reporter)
    private val failure = IllegalStateException("boom")

    private fun entry(severity: LogSeverity, throwableName: String? = null) =
        LogEntry(severity, LogTags.NETWORK, "something happened", throwableName)

    // AC-13
    @Test
    fun infoWarnAndErrorBecomeBreadcrumbsWithSeverityAndTag() {
        writer.write(entry(LogSeverity.INFO), null)
        writer.write(entry(LogSeverity.WARN), null)
        writer.write(entry(LogSeverity.ERROR), null)

        assertThat(reporter.breadcrumbs).isEqualTo(
            listOf(
                "I/Network: something happened",
                "W/Network: something happened",
                "E/Network: something happened",
            )
        )
    }

    @Test
    fun breadcrumbEndsWithTheThrowableTypeName() {
        writer.write(entry(LogSeverity.WARN, "IOException"), failure)

        assertThat(reporter.breadcrumbs).isEqualTo(listOf("W/Network: something happened (IOException)"))
    }

    // AC-12
    @Test
    fun errorWithThrowableBecomesNonFatalAfterItsBreadcrumb() {
        writer.write(entry(LogSeverity.ERROR, "IllegalStateException"), failure)

        assertThat(reporter.calls.map { it::class }).isEqualTo(
            listOf(FakeCrashReporter.Call.Breadcrumb::class, FakeCrashReporter.Call.NonFatal::class)
        )
        val report = reporter.nonFatals.single()
        assertThat(report.typeName).isEqualTo("IllegalStateException")
        assertThat(report.message).isEqualTo("something happened")
        assertThat(report.stackSource).isSameInstanceAs(failure)
    }

    // AC-12
    @Test
    fun errorWithoutThrowableIsOnlyABreadcrumb() {
        writer.write(entry(LogSeverity.ERROR), null)

        assertThat(reporter.nonFatals).isEmpty()
        assertThat(reporter.breadcrumbs.size).isEqualTo(1)
    }

    // AC-12
    @Test
    fun warnWithThrowableIsNotANonFatal() {
        writer.write(entry(LogSeverity.WARN, "IllegalStateException"), failure)

        assertThat(reporter.nonFatals).isEmpty()
    }

    @Test
    fun plainWriteWithoutThrowableOnlyLeavesABreadcrumb() {
        writer.write(entry(LogSeverity.ERROR))

        assertThat(reporter.nonFatals).isEmpty()
        assertThat(reporter.breadcrumbs.size).isEqualTo(1)
    }
}
