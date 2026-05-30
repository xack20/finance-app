// AgentRuntime.kt
package app.hisaab.agent
import app.hisaab.data.AccountRepository
import app.hisaab.data.CategoryRepository
import app.hisaab.data.InsightRepository
import app.hisaab.data.MerchantRepository
import app.hisaab.data.PersonRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.llm.LlmError
import app.hisaab.llm.LlmException
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

sealed interface AgentAvailability {
    data object Ready : AgentAvailability
    data class Unavailable(val reason: String) : AgentAvailability
}

fun buildAgentToolRegistry(
    accounts: AccountRepository, categories: CategoryRepository, merchants: MerchantRepository,
    txns: TransactionRepository, insights: InsightRepository, persons: PersonRepository,
): ToolRegistry = ToolRegistry(
    readTools = listOf(
        ListAccountsTool(accounts), AccountBalancesTool(accounts), ListCategoriesTool(categories),
        FindMerchantTool(merchants), QueryTransactionsTool(txns), SpendByCategoryTool(insights),
        PersonBalanceTool(persons), CardSummaryTool(accounts),
    ),
    writeDescriptors = agentWriteDescriptors(),
)

/** Integration seam. Loop stays db-free; the committer is the only writer. */
class AgentRuntime(
    private val registry: ToolRegistry,
    private val provider: AgentProvider?,
    private val isConsented: suspend () -> Boolean,
    private val accountNames: suspend () -> String,
    private val categoryNames: suspend () -> String,
    private val committer: WriteBatchCommitter,
    private val todayIso: () -> String = {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
    },
    private val language: String = "English/Bengali/Banglish",
    private val maxIterations: Int = 6,
) {
    suspend fun availability(): AgentAvailability = when {
        !isConsented() -> AgentAvailability.Unavailable("Turn on the assistant (a cloud feature) to continue.")
        provider == null -> AgentAvailability.Unavailable("Add a cloud model + API key in Settings to use the assistant.")
        else -> AgentAvailability.Ready
    }

    suspend fun run(history: List<ChatMessage>, userMessage: String): AgentTurnResult {
        val p = provider ?: throw LlmException(LlmError.InvalidKey)
        val system = AgentPrompts.system(registry, accounts = accountNames(), categories = categoryNames(),
            todayIso = todayIso(), language = language)
        return AgentLoop(p, registry, systemPrompt = system, maxIterations = maxIterations).run(history, userMessage)
    }

    suspend fun apply(writes: List<ProposedWrite>): AppliedSummary = committer.apply(writes)
}
