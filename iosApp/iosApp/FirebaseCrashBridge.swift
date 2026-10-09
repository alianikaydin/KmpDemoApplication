import Foundation
import FirebaseCrashlytics
import FirebaseInstallations
import Shared

/// Firebase Crashlytics behind the Kotlin `NativeCrashBridge`. Created only when the build has a
/// `GoogleService-Info.plist`, after `FirebaseApp.configure()`.
///
/// Every value that reaches this class was already redacted on the Kotlin side, and `reason` never
/// holds a throwable message.
final class FirebaseCrashBridge: NativeCrashBridge {
    private let crashlytics = Crashlytics.crashlytics()

    func setCollectionEnabled(enabled: Bool) {
        crashlytics.setCrashlyticsCollectionEnabled(enabled)
    }

    func deleteUnsentReports() {
        crashlytics.deleteUnsentReports()
    }

    /// Fire and forget: the result is not awaited and a failure is left for the next attempt.
    func deleteInstallationId() {
        Installations.installations().delete { _ in }
    }

    /// An empty id clears the user id.
    func setUserId(id: String) {
        crashlytics.setUserID(id.isEmpty ? nil : id)
    }

    func setCustomValue(key: String, value: String) {
        crashlytics.setCustomValue(value, forKey: key)
    }

    func log(message: String) {
        crashlytics.log(message)
    }

    /// The Firebase iOS SDK has no on-demand fatal API, so `fatal` does not change the call. A fatal
    /// Kotlin crash can therefore show up as two records (this one and the process abort).
    func recordError(name: String, reason: String, stackAddresses: [KotlinLong], fatal: Bool) {
        let model = ExceptionModel(name: name, reason: reason)
        model.stackTrace = stackAddresses.map { StackFrame(address: $0.uintValue) }
        crashlytics.record(exceptionModel: model)
    }
}
