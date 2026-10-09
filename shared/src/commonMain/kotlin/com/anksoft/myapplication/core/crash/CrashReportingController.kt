package com.anksoft.myapplication.core.crash

import com.anksoft.myapplication.core.concurrency.createReentrantLock
import com.anksoft.myapplication.core.config.AppEnvironment
import com.anksoft.myapplication.core.consent.ConsentManager
import com.anksoft.myapplication.core.consent.OptionalDataConsent
import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.info
import com.anksoft.myapplication.core.logging.warn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Keeps the crash vendor, the [CrashGate] and the anonymous id in line with the account's consent
 * and the environment. The rule is based on the current state, not on transitions, so a missed
 * intermediate value or a revocation made on another device is handled the same way:
 *
 * - `GRANTED` in stage or prod: set the environment key and the install id, turn the vendor on,
 *   then open the gate.
 * - `UNKNOWN`, or any state in dev: close the gate, then turn the vendor off. The id is kept, so
 *   signing out and in again does not change it.
 * - `DENIED`: as above, plus delete unsent reports, forget the id and delete the vendor's own
 *   installation id (a revocation).
 *
 * [reporter] is null when no vendor is bound (web, builds without Firebase files). The controller
 * then stays idle: no id is created and no storage is touched.
 */
class CrashReportingController(
    private val reporter: CrashReporter?,
    private val gate: CrashGate,
    private val consent: ConsentManager,
    private val installIds: InstallIdStore,
    private val environment: AppEnvironment,
    private val logger: AppLogger,
    private val scope: CoroutineScope,
) {
    private val lock = createReentrantLock()

    // Guarded by lock.
    private var started = false
    private var lastApplied: OptionalDataConsent? = null

    /**
     * Idempotent. Deletes reports left over from a period without consent, applies the current
     * consent synchronously, then follows later changes.
     */
    fun start() {
        val vendor = reporter ?: return
        val firstCall = lock.withLock {
            val first = !started
            started = true
            first
        }
        if (!firstCall) return
        // Before anything is turned on: whatever is still unsent was recorded without consent.
        guarded("deleteUnsentReports") { vendor.deleteUnsentReports() }
        applyState(vendor, consent.optionalDataConsent.value)
        scope.launch {
            consent.optionalDataConsent.collect { applyState(vendor, it) }
        }
    }

    private fun applyState(vendor: CrashReporter, state: OptionalDataConsent) {
        lock.withLock {
            // The first value is applied by start() and arrives again from the collector.
            if (state == lastApplied) return@withLock
            lastApplied = state
            when {
                environment == AppEnvironment.DEV -> pause(vendor)
                state == OptionalDataConsent.GRANTED -> enable(vendor)
                state == OptionalDataConsent.DENIED -> revoke(vendor)
                else -> pause(vendor)
            }
            logger.info(LogTags.CRASH) { "Crash reporting state=$state env=$environment" }
        }
    }

    // Vendor flag first, gate second: the flag must never be on while the gate is still closed
    // for a reason we do not know about, and the gate must never be open before the flag.
    private fun enable(vendor: CrashReporter) {
        guarded("setCustomKey") { vendor.setCustomKey("environment", environment.name) }
        guarded("setAnonymousId") { vendor.setAnonymousId(installIds.getOrCreate()) }
        guarded("setCollectionEnabled") { vendor.setCollectionEnabled(true) }
        gate.open()
    }

    // Gate first, vendor flag second: nothing may pass the gate once the flag is going off.
    private fun pause(vendor: CrashReporter) {
        gate.close()
        guarded("setCollectionEnabled") { vendor.setCollectionEnabled(false) }
    }

    private fun revoke(vendor: CrashReporter) {
        pause(vendor)
        guarded("deleteUnsentReports") { vendor.deleteUnsentReports() }
        guarded("setAnonymousId") { vendor.setAnonymousId(null) }
        guarded("resetInstallId") { installIds.reset() }
        guarded("deleteVendorInstallationId") { vendor.deleteVendorInstallationId() }
    }

    // A failing vendor must neither crash the app nor leave the remaining steps undone.
    private inline fun guarded(step: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.warn(LogTags.CRASH, e) { "Crash reporting step failed step=$step" }
        }
    }
}
