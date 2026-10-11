package com.anksoft.myapplication.core.di

import com.anksoft.myapplication.core.analytics.Analytics
import com.anksoft.myapplication.core.analytics.AnalyticsTracker
import com.anksoft.myapplication.core.analytics.NoOpAnalytics
import com.anksoft.myapplication.core.config.AppConfig
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Qualifier under which a platform binds its [Analytics] in the `platformModules` given to
 * initKoin. Nothing bound means no vendor: analytics is a no-op (web, builds without vendor
 * configuration).
 */
const val PLATFORM_ANALYTICS = "platformAnalytics"

val analyticsModule = module {
    single {
        AnalyticsTracker(
            analytics = getOrNull<Analytics>(named(PLATFORM_ANALYTICS)) ?: NoOpAnalytics,
            consent = get(),
            environment = get<AppConfig>().environment,
            preferences = get(),
            logger = get(),
            scope = get(ApplicationScope),
        )
    }
}
