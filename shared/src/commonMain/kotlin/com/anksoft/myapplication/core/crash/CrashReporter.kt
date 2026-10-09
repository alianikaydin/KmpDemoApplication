package com.anksoft.myapplication.core.crash

/**
 * Vendor-neutral crash reporting port. Implemented per platform (Firebase on Android and iOS);
 * common code only knows this interface and [NoOpCrashReporter]. Implementations must not throw,
 * must not log through AppLogger (they are called from a log writer) and must never send a
 * throwable's message.
 */
interface CrashReporter {
    /** Turns the vendor SDK's own collection on or off. */
    fun setCollectionEnabled(enabled: Boolean)

    /** Deletes reports that were stored on the device but not sent yet. */
    fun deleteUnsentReports()

    /** Deletes the vendor's own installation identifier. */
    fun deleteVendorInstallationId()

    /** Sets the anonymous install id; null clears it. Never a user id or e-mail. */
    fun setAnonymousId(id: String?)

    fun setCustomKey(key: String, value: String)

    fun addBreadcrumb(message: String)

    fun recordNonFatal(report: NonFatalReport)
}
