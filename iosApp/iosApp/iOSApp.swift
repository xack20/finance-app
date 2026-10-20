import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        // Prefer Apple's on-device model when available (iOS 26 + Apple Intelligence); the agent
        // runtime falls back to a configured cloud provider otherwise.
        if #available(iOS 26.0, *) {
            NativeAgentRegistry.shared.bridge = OnDeviceAgentBridge()
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.all)
        }
    }
}
