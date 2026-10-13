package com.anksoft.myapplication.core.logging

import kotlin.coroutines.cancellation.CancellationException

/**
 * Default [AppLogger]: drops entries below the threshold of every writer, builds the message
 * lazily, redacts it and hands the same [LogEntry] to every writer that accepts the severity.
 * A writer that throws is skipped; the others still receive the entry.
 *
 * A plain [LogWriter] follows [minSeverity] (the environment's console minimum) and only sees
 * the throwable's class name. A [RemoteLogWriter] has its own minimum and also receives the
 * throwable itself.
 */
class DispatchingLogger(
    private val minSeverity: LogSeverity,
    private val writers: List<LogWriter>
) : AppLogger {

    // The lowest threshold of all writers; below it nothing can be written, so the message is not built.
    private val lowest: LogSeverity =
        writers.minOfOrNull { it.threshold() } ?: minSeverity

    override fun log(
        severity: LogSeverity,
        tag: String,
        throwable: Throwable?,
        message: () -> String
    ) {
        if (severity < lowest) return
        val entry = LogEntry(
            severity = severity,
            tag = tag,
            message = Redactor.redact(message()),
            throwableName = throwable?.let { it::class.simpleName ?: "Throwable" }
        )
        // A broken writer (e.g. crash reporting) must not break the caller: safeCall logs from
        // inside its catch blocks. Cancellation is still propagated.
        writers.forEach { writer ->
            if (severity < writer.threshold()) return@forEach
            try {
                if (writer is RemoteLogWriter) writer.write(entry, throwable) else writer.write(entry)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    private fun LogWriter.threshold(): LogSeverity =
        if (this is RemoteLogWriter) this.minSeverity else minSeverity
}
