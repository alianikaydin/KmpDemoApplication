import SwiftUI
import FirebaseCore
import Shared

@main
struct iOSApp: App {
    init() {
        // Environment, demo permission and backend URL are fixed per build configuration
        // (Configuration/Dev|Stage|Prod.xcconfig). The shared AppConfig decides what they mean.
        let info = Bundle.main
        let environment = info.object(forInfoDictionaryKey: "KMPEnvironment") as? String ?? ""
        let backendUrl = info.object(forInfoDictionaryKey: "KMPBackendURL") as? String
        let demoAllowed = (info.object(forInfoDictionaryKey: "KMPDemoAllowed") as? String) == "YES"
        let versionName = info.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
        let versionCode = info.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? ""
        // Crash reporting exists only when the build has a GoogleService-Info.plist for its
        // environment (iosApp/Firebase/<env>/, copied in by a build phase). Without it the
        // Firebase SDK is never started and the shared code gets no bridge, so it stays a no-op.
        var crashBridge: NativeCrashBridge?
        if Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil {
            FirebaseApp.configure()
            crashBridge = FirebaseCrashBridge()
        }
        KoinIosKt.doInitKoin(
            environment: environment,
            backendUrl: backendUrl,
            demoAllowed: demoAllowed,
            versionName: versionName,
            versionCode: versionCode,
            crashBridge: crashBridge
        )
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
