package com.anksoft.myapplication.core.crash

import com.anksoft.myapplication.core.logging.Redactor

/**
 * Hands breadcrumbs and caught errors to [delegate] only while [gate] is open, and masks their
 * text once more with [Redactor]. The logger already redacts; doing it again here covers callers
 * that do not come through the logger. Control calls always pass, because closing the vendor
 * must work when the gate is closed.
 */
internal class GatedCrashReporter(
    private val delegate: CrashReporter,
    private val gate: CrashGate,
) : CrashReporter {

    override fun setCollectionEnabled(enabled: Boolean) = delegate.setCollectionEnabled(enabled)

    override fun deleteUnsentReports() = delegate.deleteUnsentReports()

    override fun deleteVendorInstallationId() = delegate.deleteVendorInstallationId()

    override fun setAnonymousId(id: String?) = delegate.setAnonymousId(id)

    override fun setCustomKey(key: String, value: String) = delegate.setCustomKey(key, value)

    override fun addBreadcrumb(message: String) {
        if (gate.isOpen) delegate.addBreadcrumb(Redactor.redact(message))
    }

    override fun recordNonFatal(report: NonFatalReport) {
        if (gate.isOpen) delegate.recordNonFatal(report.withMessage(Redactor.redact(report.message)))
    }
}
