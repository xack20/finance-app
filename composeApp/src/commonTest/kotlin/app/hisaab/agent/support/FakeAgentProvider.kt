package app.hisaab.agent.support

import app.hisaab.agent.AgentProvider
import app.hisaab.agent.ChatMessage

/** Pops canned envelope strings in order; records the messages it was called with. */
class FakeAgentProvider(scripted: List<String>) : AgentProvider {
    private val queue = ArrayDeque(scripted)
    val calls = mutableListOf<List<ChatMessage>>()
    var callCount = 0; private set
    override suspend fun complete(messages: List<ChatMessage>, maxTokens: Int): String {
        callCount++; calls.add(messages)
        return if (queue.isEmpty()) error("FakeAgentProvider exhausted") else queue.removeFirst()
    }
}
