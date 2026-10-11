package com.anksoft.myapplication.core.analytics

/** The only events the app can send. Adding one: docs/analytics-events.md. */
sealed interface AnalyticsEvent {
    data class ScreenView(val screen: AnalyticsScreen) : AnalyticsEvent
}

/** The screens that report a view. [value] is the fixed name sent to the vendor. */
enum class AnalyticsScreen(val value: String) {
    HOME("home"),
    SETTINGS("settings"),
}

internal val AnalyticsEvent.vendorName: String
    get() = when (this) {
        is AnalyticsEvent.ScreenView -> AnalyticsNames.SCREEN_VIEW
    }

/**
 * The event's own parameters. `screen_class` repeats the screen name on purpose: left out, the
 * vendor fills in a platform class name that differs between Android and iOS.
 */
internal fun AnalyticsEvent.vendorParams(): Map<String, String> = when (this) {
    is AnalyticsEvent.ScreenView -> mapOf(
        AnalyticsNames.SCREEN_NAME to screen.value,
        AnalyticsNames.SCREEN_CLASS to screen.value,
    )
}
