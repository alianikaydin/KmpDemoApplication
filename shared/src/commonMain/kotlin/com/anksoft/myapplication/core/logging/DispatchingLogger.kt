package com.anksoft.myapplication.core.logging

/**
 * Default [AppLogger]: drops entries below [minSeverity], builds the message lazily, redacts it
 * and hands the same [LogEntry] to every writer.
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
        writers.forEach { it.write(entry) }
    }
}
