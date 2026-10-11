package com.anksoft.myapplication.core.analytics

/**
 * Vendor-neutral analytics port, implemented per platform (Firebase on Android and iOS).
 * Feature code never calls it: it goes through [AnalyticsTracker], which applies the consent and
 * environment rules and builds the parameters from the typed catalog (docs/analytics-events.md).
 * Swift implements this interface directly, so only Swift-friendly types are allowed here.
 */
interface Analytics {
    fun logEvent(name: String, params: Map<String, String>)

    /** Turns the vendor's collection on or off. Off means the vendor sends and stores nothing. */
    fun setCollectionEnabled(enabled: Boolean)

    /** Deletes the analytics data the vendor holds on this device (its app instance id and queue). */
    fun resetAnalyticsData()
}
