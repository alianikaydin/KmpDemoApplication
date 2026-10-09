package com.anksoft.myapplication.core.logging

/**
 * Writer that sends entries off the device (crash reporting, later analytics). Unlike a plain
 * [LogWriter] it has its own minimum severity, independent of the environment's console minimum,
 * and it receives the throwable. It must never read the throwable's message; only its type and
 * stack frames may leave the device (see `NonFatalReport`).
 */
interface RemoteLogWriter : LogWriter {
    /** Lowest severity this writer wants, whatever the environment's console minimum is. */
    val minSeverity: LogSeverity

    fun write(entry: LogEntry, throwable: Throwable?)

    override fun write(entry: LogEntry) = write(entry, null)
}
