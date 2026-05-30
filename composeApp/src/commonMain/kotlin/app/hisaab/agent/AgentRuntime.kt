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
    data object NeedsConsent : AgentAvailability
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
    /** Resolved lazily so the runtime always reflects the user's currently-selected cloud provider. */
    private val agentProvider: suspend () -> AgentProvider?,
    private val isConsented: suspend () -> Boolean,
    private val accountNames: suspend () -> String,
    private val categoryNames: suspend () -> String,
    private val committer: WriteBatchCommitter,
    /** Distinct, actionable reason shown when no agent provider resolves (M4-7 per-gate message). */
    private val unavailableReason: suspend () -> String = {
        "Add a cloud model + API key in Settings to use the assistant."
    },
    private val todayIso: () -> String = {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
    },
    private val language: String = "English/Bengali/Banglish",
    private val maxIterations: Int = 6,
) {
    suspend fun availability(): AgentAvailability = when {
        !isConsented() -> AgentAvailability.NeedsConsent
        agentProvider() == null -> AgentAvailability.Unavailable(unavailableReason())
        else -> AgentAvailability.Ready
    }

    suspend fun run(history: List<ChatMessage>, userMessage: String): AgentTurnResult {
        val p = agentProvider() ?: throw LlmException(LlmError.InvalidKey)
        val system = AgentPrompts.system(registry, accounts = accountNames(), categories = categoryNames(),
            todayIso = todayIso(), language = language)
        return AgentLoop(p, registry, systemPrompt = system, maxIterations = maxIterations).run(history, userMessage)
    }

    suspend fun apply(writes: List<ProposedWrite>): AppliedSummary = committer.apply(writes)
}
