package com.anksoft.myapplication.core.crash

import com.anksoft.myapplication.core.concurrency.createReentrantLock
import kotlin.reflect.KClass

/** Records every call in order. [throwOn] makes the named call types throw, to test failure handling. */
class FakeCrashReporter(
    var throwOn: Set<KClass<out Call>> = emptySet(),
) : CrashReporter {

    sealed interface Call {
        data class SetCollection(val enabled: Boolean) : Call
        data object DeleteUnsent : Call
        data object DeleteVendorId : Call
        data class SetId(val id: String?) : Call
        data class CustomKey(val key: String, val value: String) : Call
        data class Breadcrumb(val message: String) : Call
        data class NonFatal(val report: NonFatalReport) : Call
    }

    // Log writers can call the reporter from several dispatchers, so access is locked.
    private val lock = createReentrantLock()
    private val _calls = mutableListOf<Call>()

    /** A snapshot of the calls so far, in order. */
    val calls: List<Call> get() = lock.withLock { _calls.toList() }

    val breadcrumbs: List<String> get() = calls.filterIsInstance<Call.Breadcrumb>().map { it.message }
    val nonFatals: List<NonFatalReport> get() = calls.filterIsInstance<Call.NonFatal>().map { it.report }

    fun clear() {
        lock.withLock { _calls.clear() }
    }

    private fun record(call: Call) {
        lock.withLock { _calls += call }
        if (call::class in throwOn) throw IllegalStateException("fake reporter failure")
    }

    override fun setCollectionEnabled(enabled: Boolean) = record(Call.SetCollection(enabled))
    override fun deleteUnsentReports() = record(Call.DeleteUnsent)
    override fun deleteVendorInstallationId() = record(Call.DeleteVendorId)
    override fun setAnonymousId(id: String?) = record(Call.SetId(id))
    override fun setCustomKey(key: String, value: String) = record(Call.CustomKey(key, value))
    override fun addBreadcrumb(message: String) = record(Call.Breadcrumb(message))
    override fun recordNonFatal(report: NonFatalReport) = record(Call.NonFatal(report))
}
