import SwiftUI
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
        KoinIosKt.doInitKoin(
            environment: environment,
            backendUrl: backendUrl,
            demoAllowed: demoAllowed,
            versionName: versionName,
            versionCode: versionCode
        )
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
