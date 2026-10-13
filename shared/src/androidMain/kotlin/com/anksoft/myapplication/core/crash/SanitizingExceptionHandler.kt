package com.anksoft.myapplication.core.crash

/**
 * Wraps the default uncaught-exception handler (Crashlytics') and hands it a [ReportedException],
 * so a fatal crash reaches the vendor without any throwable message. Falls back to the original
 * throwable if sanitizing itself fails: the crash must still be reported and the process must end.
 *
 * Pure JVM code; install it after the vendor has installed its own handler.
 */
class SanitizingExceptionHandler(
    private val delegate: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // Throwable, not Exception: sanitizing may itself run out of memory in an OOM crash.
        val safe = runCatching { ReportedException.sanitize(throwable) }.getOrDefault(throwable)
        delegate?.uncaughtException(thread, safe)
    }

    companion object {
        /** Puts the sanitizer in front of the current default handler. Calling it twice does nothing. */
        fun install() {
            val current = Thread.getDefaultUncaughtExceptionHandler()
            if (current is SanitizingExceptionHandler) return
            Thread.setDefaultUncaughtExceptionHandler(SanitizingExceptionHandler(current))
        }
    }
}
