package app.hisaab.capture.agent

import app.hisaab.agent.AccountBalancesTool
import app.hisaab.agent.AgentProvider
import app.hisaab.agent.FindSimilarTransactionsTool
import app.hisaab.agent.ListAccountsTool
import app.hisaab.agent.QueryTransactionsTool
import app.hisaab.capture.enrichWithContacts
import app.hisaab.data.AccountRepository
import app.hisaab.data.AgentMemoryRepository
import app.hisaab.data.AgentQuestionRepository
import app.hisaab.data.CaptureInboxRepository
import app.hisaab.data.CategoryRepository
import app.hisaab.data.LendBorrowRepository
import app.hisaab.data.MerchantRepository
import app.hisaab.data.PersonRepository
import app.hisaab.data.TagRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.db.HisaabDatabase
import app.hisaab.llm.LlmRouter
import app.hisaab.llm.Redactor
import kotlinx.coroutines.flow.first

/**
 * The capture agent on [db], talking to the user's active cloud model (counted against [budget]). Null when no
 * cloud AI can be used — on-device mode, no consent or no key — so messages wait in the queue.
 */
suspend fun buildCaptureAgent(
    db: HisaabDatabase,
    router: LlmRouter,
    budget: AgentCallBudget,
    redact: suspend () -> Boolean,
    contactName: (String) -> String? = { null },
): CaptureAgent? {
    val provider = router.active() as? AgentProvider ?: return null
    val accounts = AccountRepository(db)
    val categories = CategoryRepository(db)
    val persons = PersonRepository(db)
    val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
    val questions = AgentQuestionRepository(db)
    val memory = AgentMemoryRepository(db)
    return CaptureAgent(
        provider = CountingAgentProvider(provider, budget),
        readTools = listOf(
            ListAccountsTool(accounts), AccountBalancesTool(accounts), FindSimilarTransactionsTool(txns),
            QueryTransactionsTool(txns),
        ),
        context = {
            AgentContext(
                accounts = accounts.observeActive().first(),
                categories = categories.observeAll().first(),
                people = persons.observeAll().first().map { it.person.name },
                // Facts can carry sender ids and names; they leave the phone like the messages do.
                memories = memory.all().map { if (redact()) Redactor.redact(it.fact) else it.fact },
            )
        },
        committer = CaptureCommitter(db, accounts, categories, txns, persons, LendBorrowRepository(db, txns), questions, memory),
        inbox = CaptureInboxRepository(db),
        questions = questions,
        prepare = { m -> prepareForCloud(m.rawBody, contactName, redact()) },
        prepareSender = { sender -> prepareForCloud(sender, contactName, redact()) },
    )
}

/** A message as it may leave the phone: contact names added, one-time codes masked, then redacted if enabled. */
fun prepareForCloud(body: String, contactName: (String) -> String?, redact: Boolean): String {
    val masked = Redactor.maskSecrets(enrichWithContacts(body, contactName))
    return if (redact) Redactor.redact(masked) else masked
}
