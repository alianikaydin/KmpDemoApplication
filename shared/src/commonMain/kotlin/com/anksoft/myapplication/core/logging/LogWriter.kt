package com.anksoft.myapplication.core.logging

/**
 * Destination of log entries (console, crash reporting, analytics). Register an implementation
 * in Koin as a `single<LogWriter>(named("..."))` and [DispatchingLogger] picks it up.
 */
fun interface LogWriter {
    fun write(entry: LogEntry)
}
