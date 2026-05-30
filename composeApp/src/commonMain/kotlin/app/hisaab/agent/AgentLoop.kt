package app.hisaab.agent

import kotlinx.coroutines.CancellationException

data class AgentTurnResult(
    val finalMessage: String,
    val proposedWrites: List<ProposedWrite>,
    val cappedOut: Boolean,
    val updatedHistory: List<ChatMessage>,
)

/** Vendor-agnostic ReAct loop. Read tools execute in-loop (pure); writes are only proposed in `final`.
 *  Validation-rejection and repair each consume one iteration, so termination is deterministic. */
class AgentLoop(
    private val provider: AgentProvider,
    private val registry: ToolRegistry,
    private val systemPrompt: String,
    private val maxIterations: Int = 6,
    private val maxTokens: Int = 1024,
) {
    suspend fun run(history: List<ChatMessage>, userMessage: String): AgentTurnResult {
        val working = ArrayList<ChatMessage>()
        working += ChatMessage(Role.SYSTEM, systemPrompt)
        working += history
        working += ChatMessage(Role.USER, userMessage)

        var iterations = 0
        while (iterations < maxIterations) {
            iterations++
            val raw = provider.complete(working, maxTokens)
            val envelope = try {
                AgentJson.decodeEnvelope(raw)
            } catch (e: AgentDecodeException) {
                working += ChatMessage(Role.ASSISTANT, raw)
                working += ChatMessage(Role.TOOL, "ERROR: your reply was not valid JSON (${e.message}). Reply with exactly one JSON envelope.")
                continue
            }
            val action = envelope.action
            val finalResponse = envelope.final
            if (finalResponse != null) {
                val writes = finalResponse.proposedWrites
                    .filter { registry.isWrite(it.tool) }       // only known write tools survive
                    .map { ProposedWrite(it.tool, it.args) }
                working += ChatMessage(Role.ASSISTANT, raw)
                return AgentTurnResult(finalResponse.message, writes, cappedOut = false, updatedHistory = working.toList())
            }
            if (action != null) {
                working += ChatMessage(Role.ASSISTANT, raw)
                val tool = registry.read(action.tool)
                val toolMsg = when {
                    tool != null -> runCatching { tool.execute(action.args) }
                        .getOrElse { e ->
                            if (e is CancellationException) throw e
                            "ERROR executing ${action.tool}: ${e.message}"
                        }
                    registry.isWrite(action.tool) ->
                        "ERROR: ${action.tool} is a write tool; do not call it as an action — put it in final.proposedWrites."
                    else -> "ERROR: unknown tool '${action.tool}'."
                }
                working += ChatMessage(Role.TOOL, toolMsg)
                continue
            }
            working += ChatMessage(Role.ASSISTANT, raw)
            working += ChatMessage(Role.TOOL, "ERROR: envelope had neither action nor final.")
        }
        return AgentTurnResult(
            finalMessage = "I couldn't complete that in time — could you add a bit more detail?",
            proposedWrites = emptyList(), cappedOut = true, updatedHistory = working.toList(),
        )
    }
}
