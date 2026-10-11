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
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/** An [AnalyticsTracker] with test doubles; by default consent is granted in stage, in English. */
fun testTracker(
    analytics: Analytics = FakeAnalytics(),
    consent: ConsentManager = FakeConsentManager(OptionalDataConsent.GRANTED),
    environment: AppEnvironment = AppEnvironment.STAGE,
    preferences: AppPreferences = FakeAppPreferences(AppLanguage.ENGLISH),
    logger: AppLogger = NoOpLogger,
    scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher()),
    deviceLanguage: () -> String = { "en" },
) = AnalyticsTracker(analytics, consent, environment, preferences, logger, scope, deviceLanguage)
