package app.hisaab.agent

import app.hisaab.llm.LlmError
import app.hisaab.llm.LlmException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Bridge to Apple's on-device FoundationModels (iOS 26). `FoundationModels` is Swift-only, so the model
 * call lives in Swift: a Swift type conforms to this interface (callback-based — it never implements a
 * Kotlin `suspend` fun) and registers an instance via [NativeAgentRegistry] at launch.
 *
 * [complete] returns the model's raw text, or `null` on any failure/unavailability (the adapter then
 * surfaces an error and the runtime falls back to a configured cloud provider).
 */
interface NativeAgentBridge {
    fun isAvailable(): Boolean
    fun complete(prompt: String, maxTokens: Int, callback: (String?) -> Unit)
}

/** Set once from Swift (`iOSApp.init`) when iOS 26 FoundationModels is present; read at provider resolve. */
object NativeAgentRegistry {
    var bridge: NativeAgentBridge? = null
}

/** Adapts the callback [NativeAgentBridge] into the [AgentProvider] the `AgentLoop` already drives. */
class OnDeviceAgentProvider(private val bridge: NativeAgentBridge) : AgentProvider {
    override suspend fun complete(messages: List<ChatMessage>, maxTokens: Int): String =
        suspendCancellableCoroutine { cont ->
            bridge.complete(formatPrompt(messages), maxTokens) { result ->
                if (result != null) {
                    cont.resume(result)
                } else {
                    cont.resumeWithException(LlmException(LlmError.Unavailable))
                }
            }
        }

    // The cloud providers receive a structured message list; FoundationModels takes a single prompt, so
    // flatten the conversation (system instructions first, then role-labelled turns).
    private fun formatPrompt(messages: List<ChatMessage>): String = buildString {
        messages.forEach { m ->
            when (m.role) {
                Role.SYSTEM -> append(m.content).append("\n\n")
                Role.USER -> append("User: ").append(m.content).append('\n')
                Role.ASSISTANT -> append("Assistant: ").append(m.content).append('\n')
                Role.TOOL -> append("Tool result: ").append(m.content).append('\n')
            }
        }
        append("Assistant: ")
    }
}
