package com.anksoft.myapplication.core.logging

/** Collects every entry it receives. */
class RecordingLogWriter : LogWriter {
    private val _entries = mutableListOf<LogEntry>()
    val entries: List<LogEntry> get() = _entries

    override fun write(entry: LogEntry) {
        _entries += entry
    }
}

/** A real [DispatchingLogger] (so redaction is exercised) wired to a [RecordingLogWriter]. */
fun recordingLogger(
    min: LogSeverity = LogSeverity.DEBUG
): Pair<AppLogger, RecordingLogWriter> {
    val writer = RecordingLogWriter()
    return DispatchingLogger(min, listOf(writer)) to writer
}
