package com.anksoft.myapplication.crash

import android.content.Context
import com.anksoft.myapplication.core.crash.CrashReporter
import com.anksoft.myapplication.core.crash.NonFatalReport
import com.anksoft.myapplication.core.crash.ReportedException
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.installations.FirebaseInstallations

/**
 * Firebase Crashlytics behind [CrashReporter]. The only place on Android that touches the SDK.
 * It must not throw and must not log through AppLogger (it is called from a log writer), so every
 * call swallows vendor errors.
 */
class FirebaseCrashReporter private constructor(
    private val crashlytics: FirebaseCrashlytics,
) : CrashReporter {

    override fun setCollectionEnabled(enabled: Boolean) = safely {
        crashlytics.setCrashlyticsCollectionEnabled(enabled)
    }

    override fun deleteUnsentReports() = safely { crashlytics.deleteUnsentReports() }

    // Fire and forget: the result is not awaited, and a failure leaves the id for the next attempt.
    override fun deleteVendorInstallationId() = safely { FirebaseInstallations.getInstance().delete() }

    // An empty string is how Crashlytics clears the user id.
    override fun setAnonymousId(id: String?) = safely { crashlytics.setUserId(id.orEmpty()) }

    override fun setCustomKey(key: String, value: String) = safely { crashlytics.setCustomKey(key, value) }

    override fun addBreadcrumb(message: String) = safely { crashlytics.log(message) }

    override fun recordNonFatal(report: NonFatalReport) = safely {
        crashlytics.recordException(ReportedException.from(report))
    }

    @Suppress("SwallowedException") // Deliberate: see the class comment.
    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            // Intentionally silent: see the class comment.
        }
    }

    companion object {
        /**
         * Returns the reporter, or null when Firebase is not configured (no google-services.json
         * was part of this build). The app then keeps the no-op reporter.
         */
        @Suppress("SwallowedException") // Deliberate: a broken SDK setup must not stop the app.
        fun createOrNull(context: Context): CrashReporter? = try {
            if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseCrashReporter(FirebaseCrashlytics.getInstance())
        } catch (e: Exception) {
            null
        }
    }
}
