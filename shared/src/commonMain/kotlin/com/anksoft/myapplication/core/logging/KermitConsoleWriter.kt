package com.anksoft.myapplication.core.logging

import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter
import co.touchlab.kermit.LogWriter as KermitLogWriter

/**
 * Writes entries to the platform console (Logcat, Xcode console, browser console) through
 * Kermit. Kermit stays an implementation detail of this class; nothing else imports it.
 */
class KermitConsoleWriter(
    private val delegate: KermitLogWriter = platformLogWriter()
) : LogWriter {

    override fun write(entry: LogEntry) {
        val message = entry.throwableName?.let { "${entry.message} ($it)" } ?: entry.message
        // The throwable itself is deliberately not forwarded (see LogEntry).
        delegate.log(entry.severity.toKermit(), message, entry.tag, null)
    }

    private fun LogSeverity.toKermit(): Severity = when (this) {
        LogSeverity.DEBUG -> Severity.Debug
        LogSeverity.INFO -> Severity.Info
        LogSeverity.WARN -> Severity.Warn
        LogSeverity.ERROR -> Severity.Error
    }
}
