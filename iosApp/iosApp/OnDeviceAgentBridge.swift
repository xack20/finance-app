import Foundation
import ComposeApp
import FoundationModels

/// Bridges Apple's on-device FoundationModels (iOS 26, Apple Intelligence) to the Kotlin
/// `NativeAgentBridge` the agent runtime resolves. Registered from `iOSApp.init` when available; the
/// shared `AgentRuntime` prefers this over cloud, and skips cloud consent when it's available.
@available(iOS 26.0, *)
final class OnDeviceAgentBridge: NSObject, NativeAgentBridge {

    func isAvailable() -> Bool {
        if case .available = SystemLanguageModel.default.availability {
            return true
        }
        return false
    }

    func complete(prompt: String, maxTokens: Int32, callback: @escaping (String?) -> Void) {
        Task {
            do {
                let session = LanguageModelSession()
                let response = try await session.respond(to: prompt)
                callback(response.content)
            } catch {
                // Any failure (model busy, guardrail, not ready) -> nil; the Kotlin adapter surfaces an
                // error and the runtime can fall back to a configured cloud provider.
                callback(nil)
            }
        }
    }
}
