package com.anksoft.myapplication.core.crash

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isSameInstanceAs
import kotlin.test.Test

class NonFatalReportTest {

    // AC-14, AC-15
    @Test
    fun keepsTypeAndCauseTypesButNeverTheThrowableMessage() {
        val failure = IllegalStateException(
            "user@example.com token=abc123",
            RuntimeException("password=hunter2", IllegalArgumentException("https://secret.host/path"))
        )

        val report = NonFatalReport.from(failure, "Remote failure error=UNKNOWN path=auth/login status=-")

        assertThat(report.typeName).isEqualTo("IllegalStateException")
        assertThat(report.causeTypeNames).isEqualTo(listOf("RuntimeException", "IllegalArgumentException"))
        assertThat(report.message).isEqualTo("Remote failure error=UNKNOWN path=auth/login status=-")
        val everyText = listOf(report.typeName, report.message) + report.causeTypeNames
        everyText.forEach { text ->
            assertThat(text).doesNotContain("hunter2")
            assertThat(text).doesNotContain("user@example.com")
            assertThat(text).doesNotContain("secret.host")
        }
        assertThat(report.stackSource).isSameInstanceAs(failure)
    }

    @Test
    fun causeChainIsCappedAndCycleSafe() {
        val deep = (1..10).fold<Int, Throwable?>(null) { cause, _ -> RuntimeException(cause) }!!
        val loopA = CyclicException()
        val loopB = CyclicException()
        loopA.next = loopB
        loopB.next = loopA

        assertThat(NonFatalReport.from(deep, "deep").causeTypeNames).hasSize(5)
        assertThat(NonFatalReport.from(loopA, "loop").causeTypeNames).isEqualTo(listOf("CyclicException"))
    }

    @Test
    fun withMessageReplacesOnlyTheMessage() {
        val failure = IllegalStateException("x")
        val report = NonFatalReport.from(failure, "first").withMessage("second")

        assertThat(report.message).isEqualTo("second")
        assertThat(report.typeName).isEqualTo("IllegalStateException")
        assertThat(report.stackSource).isSameInstanceAs(failure)
    }
}

/** A cause chain that points back to itself, which only a custom `cause` can build in common code. */
private class CyclicException : RuntimeException("cycle") {
    var next: Throwable? = null
    override val cause: Throwable? get() = next
}
