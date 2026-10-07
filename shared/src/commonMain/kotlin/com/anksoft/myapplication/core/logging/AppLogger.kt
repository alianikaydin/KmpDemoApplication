package com.anksoft.myapplication.core.logging

/**
 * The single logging entry point for every layer. Named AppLogger because Ktor and Kermit both
 * have their own `Logger` types.
 *
 * Never pass PII (user id, email, token, password) as a message part; [Redactor] is a safety
 * net, not permission to log them.
 */
interface AppLogger {
    /**
     * Logs a message. [message] is only invoked when [severity] passes the configured minimum.
     * Only the simple class name of [throwable] is recorded.
     */
    fun log(
        severity: LogSeverity,
        tag: String,
        throwable: Throwable? = null,
        message: () -> String
    )
}

/** Logger that drops everything. For tests and template users who want logging off. */
object NoOpLogger : AppLogger {
    override fun log(
        severity: LogSeverity,
        tag: String,
        throwable: Throwable?,
        message: () -> String
    ) = Unit
}

fun AppLogger.debug(tag: String, message: () -> String) =
    log(LogSeverity.DEBUG, tag, null, message)

fun AppLogger.info(tag: String, message: () -> String) =
    log(LogSeverity.INFO, tag, null, message)

fun AppLogger.warn(tag: String, throwable: Throwable? = null, message: () -> String) =
    log(LogSeverity.WARN, tag, throwable, message)

fun AppLogger.error(tag: String, throwable: Throwable? = null, message: () -> String) =
    log(LogSeverity.ERROR, tag, throwable, message)
