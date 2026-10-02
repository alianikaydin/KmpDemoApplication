import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        // Debug builds run against the in-app mock backend; release uses the real API.
        #if DEBUG
        let useMockBackend = true
        #else
        let useMockBackend = false
        #endif
        AppModuleKt.doInitKoin(useMockBackend: useMockBackend)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
