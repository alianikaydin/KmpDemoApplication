package com.anksoft.myapplication.core.crash

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSameInstanceAs
import kotlin.test.Test

class ReportedExceptionTest {

    private fun failureWithSecrets(): Throwable = IllegalStateException(
        "alice@example.com token=abc123",
        RuntimeException("https://secret.host/path", IllegalArgumentException("password=hunter2"))
    )

    private fun Throwable.allMessages(): List<String> =
        generateSequence(this) { it.cause }.mapNotNull { it.message }.toList()

    // AC-14
    @Test
    fun messageIsTypeAndLogMessageOnly() {
        val report = NonFatalReport.from(failureWithSecrets(), "Remote failure error=UNKNOWN path=auth/login status=-")

        val reported = ReportedException.from(report)

        assertThat(reported.message)
            .isEqualTo("IllegalStateException: Remote failure error=UNKNOWN path=auth/login status=-")
    }

    // AC-18 (Android): the vendor groups by the original frames.
    @Test
    fun stackFramesAreCopiedFromTheOriginal() {
        val original = failureWithSecrets()

        val reported = ReportedException.from(NonFatalReport.from(original, "failed"))

        assertThat(reported.stackTrace.toList()).isEqualTo(original.stackTrace.toList())
    }

    // AC-14
    @Test
    fun causesKeepTypesAndStacksButNoMessages() {
        val original = failureWithSecrets()

        val reported = ReportedException.from(NonFatalReport.from(original, "failed"))

        val causes = generateSequence(reported.cause) { it.cause }.toList()
        assertThat(causes.map { it.message }).isEqualTo(listOf("RuntimeException", "IllegalArgumentException"))
        assertThat(causes[0].stackTrace.toList()).isEqualTo(original.cause!!.stackTrace.toList())
        reported.allMessages().forEach { text ->
            assertThat(text).doesNotContain("alice@example.com")
            assertThat(text).doesNotContain("secret.host")
            assertThat(text).doesNotContain("hunter2")
        }
        assertThat(reported.cause).isNotNull().isInstanceOf(ReportedException::class)
    }

    @Test
    fun sanitizedThrowableIsJustItsTypeNameWithTheOriginalStack() {
        val original = failureWithSecrets()

        val sanitized = ReportedException.sanitize(original)

        assertThat(sanitized.message).isEqualTo("IllegalStateException")
        assertThat(sanitized.stackTrace.toList()).isEqualTo(original.stackTrace.toList())
        assertThat(sanitized.allMessages()).isEqualTo(
            listOf("IllegalStateException", "RuntimeException", "IllegalArgumentException")
        )
    }

    @Test
    fun deepCauseChainsAreCapped() {
        val deep = (1..10).fold<Int, Throwable?>(null) { cause, _ -> RuntimeException(cause) }!!

        val chain = generateSequence<Throwable>(ReportedException.sanitize(deep)) { it.cause }.toList()

        assertThat(chain).hasSize(6)
    }

    // S3 (fatal path)
    @Test
    fun sanitizingHandlerForwardsASanitizedThrowableToTheDelegate() {
        var received: Throwable? = null
        val handler = SanitizingExceptionHandler(Thread.UncaughtExceptionHandler { _, t -> received = t })

        handler.uncaughtException(Thread.currentThread(), failureWithSecrets())

        val forwarded = received
        assertThat(forwarded).isNotNull().isInstanceOf(ReportedException::class)
        assertThat(forwarded!!.message).isEqualTo("IllegalStateException")
        assertThat(forwarded.allMessages()).doesNotContain("alice@example.com token=abc123")
    }

    @Test
    fun sanitizingHandlerFallsBackToTheOriginalIfSanitizingFails() {
        var received: Throwable? = null
        val handler = SanitizingExceptionHandler(Thread.UncaughtExceptionHandler { _, t -> received = t })
        val hostile = object : RuntimeException("original") {
            // A throwable whose stack trace cannot be read makes the copy fail.
            override fun getStackTrace(): Array<StackTraceElement> = error("unreadable stack")
        }

        handler.uncaughtException(Thread.currentThread(), hostile)

        assertThat(received).isNotNull().isSameInstanceAs(hostile)
    }

    @Test
    fun installPutsTheSanitizerInFrontOfTheCurrentDefaultHandlerOnlyOnce() {
        val before = Thread.getDefaultUncaughtExceptionHandler()
        try {
            SanitizingExceptionHandler.install()
            val first = Thread.getDefaultUncaughtExceptionHandler()
            SanitizingExceptionHandler.install()

            assertThat(first).isInstanceOf(SanitizingExceptionHandler::class)
            assertThat(Thread.getDefaultUncaughtExceptionHandler()).isSameInstanceAs(first)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before)
        }
    }
}
