package com.anksoft.myapplication.core.analytics

/** Used where no analytics vendor is bound (web, builds without vendor configuration). */
object NoOpAnalytics : Analytics {
    override fun logEvent(name: String, params: Map<String, String>) = Unit

    override fun setCollectionEnabled(enabled: Boolean) = Unit

    override fun resetAnalyticsData() = Unit
}
