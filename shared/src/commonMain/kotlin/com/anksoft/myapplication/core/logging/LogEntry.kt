package com.anksoft.myapplication.core.logging

/**
 * One log line, already redacted, as handed to every [LogWriter].
 *
 * @property tag One of the constants in [LogTags].
 * @property message Message text with sensitive values masked by [Redactor].
 * @property throwableName Simple class name of the attached throwable, if any. The throwable
 * itself never reaches a writer, so its message and stack trace cannot leak.
 */
data class LogEntry(
    val severity: LogSeverity,
    val tag: String,
    val message: String,
    val throwableName: String? = null
)
