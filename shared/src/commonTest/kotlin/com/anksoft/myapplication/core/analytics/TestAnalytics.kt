package com.anksoft.myapplication.core.analytics

import com.anksoft.myapplication.core.config.AppEnvironment
import com.anksoft.myapplication.core.consent.ConsentManager
import com.anksoft.myapplication.core.consent.FakeConsentManager
import com.anksoft.myapplication.core.consent.OptionalDataConsent
import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.logging.NoOpLogger
import com.anksoft.myapplication.core.preferences.AppLanguage
import com.anksoft.myapplication.core.preferences.AppPreferences
import com.anksoft.myapplication.core.preferences.FakeAppPreferences
import kotlinx.coroutines.CoroutineScope

/**
 * An [AnalyticsTracker] with test doubles; by default consent is granted in stage, in English.
 * [scope] has no default on purpose: a test that calls `start()` must pass `backgroundScope` (or a
 * scope it cancels), or the collector would outlive the test.
 */
fun testTracker(
    scope: CoroutineScope,
    analytics: Analytics = FakeAnalytics(),
    consent: ConsentManager = FakeConsentManager(OptionalDataConsent.GRANTED),
    environment: AppEnvironment = AppEnvironment.STAGE,
    preferences: AppPreferences = FakeAppPreferences(AppLanguage.ENGLISH),
    logger: AppLogger = NoOpLogger,
    deviceLanguage: () -> String = { "en" },
) = AnalyticsTracker(analytics, consent, environment, preferences, logger, scope, deviceLanguage)
