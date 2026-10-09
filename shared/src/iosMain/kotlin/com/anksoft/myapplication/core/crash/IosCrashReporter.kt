package com.anksoft.myapplication.core.crash

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.getStackTraceAddresses

/**
 * [CrashReporter] on top of the Swift bridge. It sends the type name, our own text and the
 * Kotlin stack addresses of an error, never the throwable's message.
 */
internal class IosCrashReporter(private val bridge: NativeCrashBridge) : CrashReporter {

    override fun setCollectionEnabled(enabled: Boolean) = bridge.setCollectionEnabled(enabled)

    override fun deleteUnsentReports() = bridge.deleteUnsentReports()

    override fun deleteVendorInstallationId() = bridge.deleteInstallationId()

    override fun setAnonymousId(id: String?) = bridge.setUserId(id.orEmpty())

    override fun setCustomKey(key: String, value: String) = bridge.setCustomValue(key, value)

    override fun addBreadcrumb(message: String) = bridge.log(message)

    override fun recordNonFatal(report: NonFatalReport) = send(report, fatal = false)

    /** Called by the unhandled-exception hook just before the process ends. Never throws. */
    fun recordUncaught(throwable: Throwable) {
        runCatching { send(NonFatalReport.from(throwable, UNCAUGHT_MESSAGE), fatal = true) }
    }

    @OptIn(ExperimentalNativeApi::class)
    private fun send(report: NonFatalReport, fatal: Boolean) {
        bridge.recordError(
            name = report.typeName,
            reason = report.reason(),
            stackAddresses = report.stackSource.getStackTraceAddresses(),
            fatal = fatal,
        )
    }

    private companion object {
        const val UNCAUGHT_MESSAGE = "Uncaught Kotlin exception"
    }
}

/** `"<message> | causes: A > B"`; the cause part is left out when there are none. Never a throwable message. */
internal fun NonFatalReport.reason(): String =
    if (causeTypeNames.isEmpty()) message else "$message | causes: ${causeTypeNames.joinToString(" > ")}"
