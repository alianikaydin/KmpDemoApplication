package com.anksoft.myapplication.core.analytics

import com.anksoft.myapplication.core.config.AppEnvironment
import com.anksoft.myapplication.core.consent.ConsentManager
import com.anksoft.myapplication.core.consent.OptionalDataConsent
import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.debug
import com.anksoft.myapplication.core.logging.info
import com.anksoft.myapplication.core.logging.warn
import com.anksoft.myapplication.core.preferences.AppPreferences
import com.anksoft.myapplication.core.preferences.deviceLanguageTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * The one entry point for product analytics. Sends a typed event only while the account's consent
 * is GRANTED and the build is not dev; nothing is queued (an event without consent is dropped).
 * [start] keeps the vendor's collection flag in line with the same rule and resets the vendor's
 * analytics data whenever the rule turns collection off (revocation, sign-out, unknown).
 */
class AnalyticsTracker(
    private val analytics: Analytics,
    private val consent: ConsentManager,
    private val environment: AppEnvironment,
    private val preferences: AppPreferences,
    private val logger: AppLogger,
    private val scope: CoroutineScope,
    private val deviceLanguage: () -> String = ::deviceLanguageTag,
) {

    /**
     * Called once at startup. Applies the current consent before it returns, so collection is off
     * before the first screen exists, then follows every change from one collector.
     */
    fun start() {
        // UNDISPATCHED runs the collector up to its first suspension on this thread, which is
        // after it has applied the current value.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            consent.optionalDataConsent.collect { state -> applyConsent(state) }
        }
    }

    /**
     * Reports [event] with the app language. The consent is read now, at send time: an event that
     * arrives without consent is gone and is not sent when consent is granted later. In dev the
     * event is only written to the debug log.
     */
    fun track(event: AnalyticsEvent) {
        val language = analyticsLanguage(preferences.language.value, deviceLanguage)
        val params = event.vendorParams() + (AnalyticsNames.LANGUAGE to language)
        val allowed = isAllowed(consent.optionalDataConsent.value)
        // Only catalog names and fixed values: no personal data can reach the log.
        logger.debug(LogTags.ANALYTICS) {
            "event=${event.vendorName} ${params.entries.joinToString(" ") { "${it.key}=${it.value}" }} " +
                "forwarded=$allowed"
        }
        if (allowed) {
            guarded("logEvent") { analytics.logEvent(event.vendorName, params) }
        }
    }

    private fun applyConsent(state: OptionalDataConsent) {
        val allowed = isAllowed(state)
        if (allowed) {
            guarded("enable") { analytics.setCollectionEnabled(true) }
        } else {
            // Every state without collection resets, not only the change into it: the rule keeps
            // no memory, so a restart or a missed intermediate value still ends clean.
            guarded("disable") { analytics.setCollectionEnabled(false) }
            guarded("reset") { analytics.resetAnalyticsData() }
        }
        logger.info(LogTags.ANALYTICS) {
            "Analytics collection enabled=$allowed state=$state env=$environment"
        }
    }

    private fun isAllowed(state: OptionalDataConsent): Boolean =
        state == OptionalDataConsent.GRANTED && environment != AppEnvironment.DEV

    // A failing vendor must not crash the app or stop the steps after it (disable fails, reset still runs).
    private fun guarded(step: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.warn(LogTags.ANALYTICS, e) { "Analytics step failed step=$step" }
        }
    }
}
