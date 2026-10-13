package com.anksoft.myapplication.core.crash

/** Reporter that does nothing: web, builds without vendor configuration and tests. */
object NoOpCrashReporter : CrashReporter {
    override fun setCollectionEnabled(enabled: Boolean) = Unit
    override fun deleteUnsentReports() = Unit
    override fun deleteVendorInstallationId() = Unit
    override fun setAnonymousId(id: String?) = Unit
    override fun setCustomKey(key: String, value: String) = Unit
    override fun addBreadcrumb(message: String) = Unit
    override fun recordNonFatal(report: NonFatalReport) = Unit
}
