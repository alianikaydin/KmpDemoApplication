package com.anksoft.myapplication.core.logging

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class DispatchingLoggerTest {

    // AC-3
    @Test
    fun entriesBelowMinimumAreDroppedAndMessageIsNeverBuilt() {
        val (logger, writer) = recordingLogger(min = LogSeverity.WARN)
        var built = false

        logger.debug("T") { built = true; "debug" }
        logger.info("T") { built = true; "info" }
        logger.warn("T") { "warn" }
        logger.error("T") { "error" }

        assertThat(built).isFalse()
        assertThat(writer.entries.map { it.severity })
            .isEqualTo(listOf(LogSeverity.WARN, LogSeverity.ERROR))
    }

    @Test
    fun everyWriterReceivesTheSameEntry() {
        val first = RecordingLogWriter()
        val second = RecordingLogWriter()
        val logger = DispatchingLogger(LogSeverity.DEBUG, listOf(first, second))

        logger.info(LogTags.APP) { "hello" }

        assertThat(first.entries).hasSize(1)
        assertThat(second.entries).isEqualTo(first.entries)
    }

    @Test
    fun throwingWriterDoesNotBreakCallerOrOtherWriters() {
        val recording = RecordingLogWriter()
        val broken = object : LogWriter {
            override fun write(entry: LogEntry) {
                throw IllegalStateException("boom")
            }
        }
        val logger = DispatchingLogger(LogSeverity.DEBUG, listOf(broken, recording))

        logger.error("T") { "still delivered" }

        assertThat(recording.entries.single().message).isEqualTo("still delivered")
    }

    @Test
    fun writerCancellationIsPropagated() {
        val cancelling = object : LogWriter {
            override fun write(entry: LogEntry) {
                throw CancellationException("cancelled")
            }
        }
        val logger = DispatchingLogger(LogSeverity.DEBUG, listOf(cancelling))

        assertFailsWith<CancellationException> { logger.error("T") { "x" } }
    }

    @Test
    fun noWritersMeansNothingHappens() {
        val logger = DispatchingLogger(LogSeverity.DEBUG, emptyList())

        logger.error("T") { "ignored" }
    }

    // AC-4
    @Test
    fun messageIsRedactedBeforeReachingWriters() {
        val (logger, writer) = recordingLogger()

        logger.info("T") { "login user@example.com token=abc" }

        assertThat(writer.entries.single().message).isEqualTo("login *** token=***")
    }

    // AC-10
    @Test
    fun throwableIsReducedToItsClassName() {
        val (logger, writer) = recordingLogger()

        logger.error("T", IllegalStateException("password=hunter2")) { "boom" }

        val entry = writer.entries.single()
        assertThat(entry.throwableName).isEqualTo("IllegalStateException")
        assertThat(entry.message).doesNotContain("hunter2")
    }

    @Test
    fun entryWithoutThrowableHasNoThrowableName() {
        val (logger, writer) = recordingLogger()

        logger.warn("T") { "plain" }

        assertThat(writer.entries.single().throwableName).isNull()
    }

    @Test
    fun noOpLoggerNeverBuildsTheMessage() {
        var built = false

        NoOpLogger.error("T") { built = true; "x" }

        assertThat(built).isFalse()
    }

    @Test
    fun recordingLoggerStartsEmpty() {
        val (_, writer) = recordingLogger()

        assertThat(writer.entries).isEmpty()
    }
}
