package com.anksoft.myapplication.core.crash

import com.anksoft.myapplication.core.logging.LogEntry
import com.anksoft.myapplication.core.logging.LogSeverity
import com.anksoft.myapplication.core.logging.RemoteLogWriter

/**
 * Turns INFO and above log entries into breadcrumbs and ERROR entries with a throwable into
 * non-fatal reports. It never reads the throwable's message.
 */
class CrashLogWriter(private val reporter: CrashReporter) : RemoteLogWriter {

    override val minSeverity: LogSeverity = LogSeverity.INFO

    override fun write(entry: LogEntry, throwable: Throwable?) {
        // The breadcrumb goes first so the non-fatal report contains its own line.
        reporter.addBreadcrumb(entry.toBreadcrumb())
        if (entry.severity == LogSeverity.ERROR && throwable != null) {
            reporter.recordNonFatal(NonFatalReport.from(throwable, entry.message))
        }
    }

    private fun LogEntry.toBreadcrumb(): String {
        val suffix = throwableName?.let { " ($it)" }.orEmpty()
        return "${severity.name.first()}/$tag: $message$suffix"
    }
}
