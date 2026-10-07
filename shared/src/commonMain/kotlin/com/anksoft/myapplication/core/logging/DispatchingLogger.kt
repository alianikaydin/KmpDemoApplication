package com.anksoft.myapplication.core.logging

import kotlin.coroutines.cancellation.CancellationException

/**
 * Default [AppLogger]: drops entries below [minSeverity], builds the message lazily, redacts it
 * and hands the same [LogEntry] to every writer. A writer that throws is skipped; the others
 * still receive the entry.
 */
class DispatchingLogger(
    private val minSeverity: LogSeverity,
    private val writers: List<LogWriter>
) : AppLogger {

    override fun log(
        severity: LogSeverity,
        tag: String,
        throwable: Throwable?,
        message: () -> String
    ) {
        if (severity < minSeverity) return
        val entry = LogEntry(
            severity = severity,
            tag = tag,
            message = Redactor.redact(message()),
            throwableName = throwable?.let { it::class.simpleName ?: "Throwable" }
        )
        // A broken writer (e.g. crash reporting) must not break the caller: safeCall logs from
        // inside its catch blocks. Cancellation is still propagated.
        writers.forEach { writer ->
            try {
                writer.write(entry)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }
}
