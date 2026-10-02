import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        // Debug builds run against the in-app mock backend unless a backend URL is configured;
        // release uses the real API.
        #if DEBUG
        let isDebug = true
        #else
        let isDebug = false
        #endif
        AppModuleKt.doInitKoin(isDebug: isDebug, backendUrl: nil)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
